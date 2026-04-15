package redsmods.flashplus.mixin.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.*;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.flashplus.FlashplusClient;
import redsmods.flashplus.PanoramaScreenshotHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.*;

@Mixin(value = ExportJob.class, remap = false)
public abstract class ExportJobMixin {

	// =========================================================================
	// Shadowed fields
	// =========================================================================

	@Shadow @Final
	private ExportSettings settings;

	@Shadow
	private double currentTickDouble;

	// =========================================================================
	// Depth export fields
	// =========================================================================

	@Unique
	private static ByteBuffer flashPlus$depthByteBuffer = null;
	@Unique
	private static FloatBuffer flashPlus$depthFloatBuffer = null;
	@Unique
	private String flashPlus$depthFramesPath;
	@Unique
	private int flashPlus$exportX;
	@Unique
	private int flashPlus$exportY;
	@Unique
	private FloatBuffer flashPlus$rawDepthBuffer;
	@Unique
	private ByteBuffer flashPlus$linearDepthBuffer;

	// =========================================================================
	// Camera JSON / entity tracking fields
	// =========================================================================

	@Unique
	private List<Map<String, Object>> flashPlus$allCameraKeyframes;

	@Unique
	private List<Map<String, Object>> flashPlus$trackedData;

	@Unique
	private Gson flashPlus$gson;

	@Unique
	private float flashPlus$previousFov;

	// Feature flags
	private boolean cameraJson = true;
	private boolean entityTracking = true;
	private int flashPlus$tick = 0;
	private Integer originalFov = 70;

	// =========================================================================
	// 1. INIT — runs once at the start of doExport, right after renderStartTime
	//    is set. Initialises both the depth writer and the JSON data structures.
	// =========================================================================

	@Inject(
			method = "doExport",
			at = @At(
					value = "FIELD",
					target = "Lcom/moulberry/flashback/exporting/ExportJob;renderStartTime:J",
					shift = At.Shift.AFTER
			),
			remap = false
	)
	private void flashPlus$init(
			VideoWriter videoWriter,
			SaveableFramebufferQueue downloader,
			CallbackInfo ci) {

		// ── Camera / entity JSON ──────────────────────────────────────────────
		this.flashPlus$allCameraKeyframes = new ArrayList<>();
		this.flashPlus$trackedData        = new ArrayList<>();
		this.flashPlus$gson               = new GsonBuilder().setPrettyPrinting().create();
		this.flashPlus$tick               = 0;
		this.flashPlus$previousFov        = FlashplusClient.fov;

		String basePath = flashPlus$basePath();
		this.flashPlus$depthFramesPath = basePath + "_depth/";
		java.io.File dir = new java.io.File(this.flashPlus$depthFramesPath);
		if (!dir.exists()) dir.mkdirs();
	}

	@Unique
	private void flashPlus$captureDepthBuffer(RenderTarget renderTarget) {
		GpuTexture depthTex = renderTarget.getDepthTexture();
		if (depthTex == null) return;

		int width = renderTarget.width;
		int height = renderTarget.height;

		// 1. Ensure our local buffers are ready
		if (flashPlus$rawDepthBuffer == null || flashPlus$exportX != width || flashPlus$exportY != height) {
			if (flashPlus$linearDepthBuffer != null) MemoryUtil.memFree(flashPlus$linearDepthBuffer);
			flashPlus$exportX = width;
			flashPlus$exportY = height;
			flashPlus$linearDepthBuffer = MemoryUtil.memAlloc(width * height * 4);
			flashPlus$rawDepthBuffer = flashPlus$linearDepthBuffer.asFloatBuffer();
		}

		// 2. Access the Texture ID via the implementation class
		// In Fabric/Mojang mappings, GpuTexture is usually implemented by an OpenGL-specific class.
		// We can cast it to find the ID.
		// Check your IDE for 'GlGpuTexture' or similar implementation names.
		try {
			// This is a common pattern in the new internal Mojang API:
			// Objects have a hidden/internal 'id' or 'handle'
			java.lang.reflect.Method getHandle = depthTex.getClass().getDeclaredMethod("getGlId");
			getHandle.setAccessible(true);
			int glId = (int) getHandle.invoke(depthTex);

			// 3. Manual Readback using a temporary FBO
			int tempFbo = GlStateManager._glGenBuffers();
			GlStateManager._glBindFramebuffer(36160, tempFbo);
			GlStateManager._glFramebufferTexture2D(36160, 36096, 3553, glId, 0);

			GL11.glReadPixels(0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, flashPlus$rawDepthBuffer);

			GlStateManager._glBindFramebuffer(36160, 0);
			GlStateManager._glDeleteFramebuffers(tempFbo);

		} catch (Exception e) {
			// If reflection fails, the mod is likely running on a non-OpenGL backend (like Metal/Vulkan)
			System.err.println("[FlashPlus] Could not retrieve GL ID from GpuTexture. Depth export failed.");
		}

		flashPlus$saveDepthFrame(width, height, flashPlus$tick);
	}

	// =========================================================================
	// 3. PER-FRAME (AFTER startDownload) — camera & entity data capture
	// =========================================================================

	@Inject(
			method = "doExport",
			at = @At(
					value = "INVOKE",
					target = "Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;startDownload(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/moulberry/flashback/exporting/SaveableFramebuffer;Z)V",
					shift = At.Shift.AFTER
			),
			remap = false
	)
	private void flashPlus$captureFrameData(
			VideoWriter videoWriter,
			SaveableFramebufferQueue downloader,
			CallbackInfo ci) {

		Minecraft minecraft = Minecraft.getInstance();
		ReplayServer replayServer = Flashback.getReplayServer();
		if (replayServer == null) return;

		double partialClientTick = this.currentTickDouble - (int) this.currentTickDouble;

		boolean isPanorama = FlashplusClient.getConfig().takePanorama
				&& settings.endTick() == settings.startTick();

		if (FlashplusClient.getConfig().cjson && !isPanorama) {
			flashPlus$captureCameraKeyframe(flashPlus$tick, partialClientTick, replayServer);
		}

		if (FlashplusClient.getConfig().etjson && !isPanorama) {
			flashPlus$captureEntityData(flashPlus$tick, partialClientTick);
		}

		flashPlus$tick++;
	}

	// =========================================================================
	// 4. FINISH — runs before the final drain call
	//    writes camera/entity JSON files.
	// =========================================================================

	@Inject(
			method = "doExport",
			at = @At(
					value = "INVOKE",
					target = "Lcom/moulberry/flashback/exporting/ExportJob;submitDownloadedFrames(Lcom/moulberry/flashback/exporting/VideoWriter;Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;Z)V",
					ordinal = 1,
					shift = At.Shift.BEFORE
			),
			remap = false
	)
	private void flashPlus$finish(
			VideoWriter videoWriter,
			SaveableFramebufferQueue downloader,
			CallbackInfo ci) throws IOException {

		// ── Apply FOV smoothing ───────────────────────────────────────────────
		flashPlus$applySmoothingToFov();

		// ── Determine base output path ────────────────────────────────────────
		String pathStr  = this.settings.output().toAbsolutePath().toString();
		int lastDot     = pathStr.lastIndexOf('.');
		String basePath = lastDot > 0 ? pathStr.substring(0, lastDot) : pathStr;

		// ── Write camera JSON ─────────────────────────────────────────────────
		if (cameraJson && !flashPlus$allCameraKeyframes.isEmpty()) {
			Path cameraJsonPath = Path.of(basePath + "CJ.json");
			try (FileWriter writer = new FileWriter(cameraJsonPath.toFile())) {
				flashPlus$gson.toJson(Map.of("keyframes", flashPlus$allCameraKeyframes), writer);
				System.out.println("[FlashPlus] Camera keyframes exported to " + cameraJsonPath);
			} catch (IOException e) {
				System.err.println("[FlashPlus] Failed to write camera keyframes:");
				e.printStackTrace();
			}
		}

		// ── Write entity tracking JSON ────────────────────────────────────────
		if (entityTracking) {
			System.out.println(flashPlus$trackedData);
		}
		if (entityTracking && !FlashplusClient.trackedmodels.isEmpty() && !flashPlus$trackedData.isEmpty()) {
			Path entityJsonPath = Path.of(basePath + "ET.json");
			try (FileWriter writer = new FileWriter(entityJsonPath.toFile())) {
				flashPlus$gson.toJson(Map.of("Entities", flashPlus$trackedData), writer);
				System.out.println("[FlashPlus] Entity tracking keyframes exported to " + entityJsonPath);
			} catch (IOException e) {
				System.err.println("[FlashPlus] Failed to write entity tracking keyframes:");
				e.printStackTrace();
			}
		}
	}

	// =========================================================================
	// 5. PANORAMA — override camera yaw/pitch/FOV right after keyframes apply
	// =========================================================================

	@Inject(
			method = "doExport",
			at = @At(
					value = "INVOKE",
					target = "Lcom/moulberry/flashback/state/EditorState;applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;F)V",
					shift = At.Shift.AFTER
			)
	)
	private void overrideCameraForPanorama(
			VideoWriter videoWriter,
			SaveableFramebufferQueue downloader,
			CallbackInfo ci) {

		if (this.settings.startTick() != this.settings.endTick()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		mc.player.setYRot(this.settings.initialCameraYaw());
		mc.player.setXRot(this.settings.initialCameraPitch());
		mc.player.yRotO = this.settings.initialCameraYaw();
		mc.player.xRotO = this.settings.initialCameraPitch();

		EditorState editorState = EditorStateManager.getCurrent();
		if (editorState != null && editorState.replayVisuals.overrideFov) {
			editorState.replayVisuals.overrideFovAmount = 90;
		} else {
			originalFov = mc.options.fov().get();
			mc.options.fov().set(90);
		}

		mc.gameRenderer.getMainCamera().setup(
				mc.level,
				mc.player,
				!mc.options.getCameraType().isFirstPerson(),
				mc.options.getCameraType().isMirrored(),
				1.0f
		);
	}

	// =========================================================================
	// 6. RETURN — panorama stitching once the full export queue drains
	// =========================================================================

	@Inject(method = "doExport", at = @At("RETURN"))
	private void onExportFinish(CallbackInfo ci) {
		if (ExportJobQueue.count() != 0 || !FlashplusClient.getConfig().takePanorama) return;

		ExportJobQueue.drainingQueue = false;
		Minecraft mc = Minecraft.getInstance();
		mc.options.fov().set(originalFov);

		Path outputPath = this.settings.output();
		Path folder     = outputPath.getParent();
		String fileName = outputPath.getFileName().toString();

		String nameWithoutExtension = fileName.contains(".")
				? fileName.substring(0, fileName.lastIndexOf("."))
				: fileName;

		String baseName = nameWithoutExtension.contains("_")
				? nameWithoutExtension.substring(0, nameWithoutExtension.lastIndexOf("_"))
				: nameWithoutExtension;

		int size = this.settings.resolutionX();

		new Thread(() -> {
			PanoramaScreenshotHelper.convertCubemapToEquirectangular(folder, baseName, size);
			System.out.println("Panorama conversion complete!");
		}).start();
	}

	// =========================================================================
	// Unique helpers
	// =========================================================================

	@Unique
	private String flashPlus$basePath() {
		String pathStr = this.settings.output().toAbsolutePath().toString();
		int lastDot = pathStr.lastIndexOf('.');
		return lastDot > 0 ? pathStr.substring(0, lastDot) : pathStr;
	}

	@Unique
	private void flashPlus$captureCameraKeyframe(
			int tickIndex, double partialClientTick, ReplayServer replayServer) {

		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (camera == null) return;

		Map<String, Object> keyframeData = new HashMap<>();
		keyframeData.put("tick", tickIndex);

		Vec3 positionVec3 = camera.position();
		keyframeData.put("position", new double[]{positionVec3.x, positionVec3.y, positionVec3.z});

		if (FlashplusClient.getConfig().useQuaternion) {
			keyframeData.put("w", FlashplusClient.quaternion.w);
			keyframeData.put("x", FlashplusClient.quaternion.x);
			keyframeData.put("y", FlashplusClient.quaternion.y);
			keyframeData.put("z", FlashplusClient.quaternion.z);
		} else {
			keyframeData.put("yaw",   camera.getYRot());
			keyframeData.put("pitch", camera.getXRot());
			keyframeData.put("roll",  FlashplusClient.roll);
		}

		float currentOverrideFov = replayServer.getEditorState().replayVisuals.overrideFovAmount;
		float keyframeEndFov     = FlashplusClient.getFOV();
		final float EPSILON      = 0.001f;
		boolean isOverrideDifferent = Math.abs(currentOverrideFov - keyframeEndFov) > EPSILON;
		float targetFov          = isOverrideDifferent ? currentOverrideFov : keyframeEndFov;
		float interpolatedFov    = (float)(flashPlus$previousFov + (targetFov - flashPlus$previousFov) * partialClientTick);

		keyframeData.put("fov",  keyframeEndFov);
		keyframeData.put("time", Minecraft.getInstance().level.getDayTime() % 24000);

		flashPlus$allCameraKeyframes.add(keyframeData);
	}

	@Unique
	private void flashPlus$captureEntityData(int tickIndex, double partialClientTick) {
		if (FlashplusClient.trackedmodels.isEmpty()) return;

		Map<String, Object> keyframeData = new HashMap<>();
		keyframeData.put("tick", tickIndex);

		for (Map<String, Object> currentModelMap : FlashplusClient.trackedmodels) {
			for (Map.Entry<String, Object> entry : currentModelMap.entrySet()) {
				String key      = entry.getKey();
				String[] keyParts = key.split("/");
				if (keyParts.length != 2) continue;

				String entityName = keyParts[0];
				String partName   = keyParts[1];

				Map<String, Object> partData = new HashMap<>();

				Entity entity = null;
				try {
					for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
						if (e.getUUID().equals(UUID.fromString(entityName))) {
							entity = e;
							break;
						}
					}
				} catch (IllegalArgumentException e) {
					continue;
				}

				if (entity == null) continue;

				EntityRenderer<?, ?> renderer =
						Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);

				if ("Eyes".equals(partName)) {
					float tick = Math.max(0.0f, Math.min(1.0f, (float)(partialClientTick + 0.001)));
					Vec3 eyePos = entity.getPosition(tick);
					partData.put("eyePosition", new double[]{
							eyePos.x, eyePos.y + entity.getEyeHeight(), eyePos.z});
					partData.put("eyeangle", new double[]{
							entity.getViewXRot((float) partialClientTick),
							entity.getViewYRot((float) partialClientTick),
							0.0});

				} else if ("BlockPosition".equals(partName)) {
					Vec3 blockPos = entity.getPosition((float) partialClientTick);
					partData.put("blockPosition", new double[]{blockPos.x, blockPos.y, blockPos.z});
					partData.put("entityrotation", new double[]{entity.getYRot(), entity.getYRot(), 0.0});

				} else {
					if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer) {
						ModelPart part = flashPlus$getNamedModelPart(livingRenderer.getModel(), partName);
						if (part != null) {
							partData.put("position", new double[]{part.x, part.y, part.z});
							partData.put("rotation", new double[]{part.xRot, part.yRot, part.zRot});
						}
					}
				}

				@SuppressWarnings("unchecked")
				Map<String, Object> entityData = (Map<String, Object>) keyframeData.get(entityName);
				if (entityData == null) {
					entityData = new HashMap<>();
					keyframeData.put(entityName, entityData);
				}
				entityData.put(partName, partData);
			}
		}

		if (keyframeData.size() > 1) {
			flashPlus$trackedData.add(keyframeData);
		}
	}

	@Unique
	private void flashPlus$applySmoothingToFov() {
		if (flashPlus$allCameraKeyframes == null || flashPlus$allCameraKeyframes.size() < 7) return;

		final int KERNEL_RADIUS = 3;
		final float[] GAUSSIAN_KERNEL = {0.006f, 0.061f, 0.242f, 0.383f, 0.242f, 0.061f, 0.006f};

		List<Float> originalFovs = new ArrayList<>();
		for (Map<String, Object> keyframe : flashPlus$allCameraKeyframes) {
			Object fovObj = keyframe.get("fov");
			originalFovs.add(fovObj instanceof Number ? ((Number) fovObj).floatValue() : 0.0f);
		}

		int listSize = originalFovs.size();
		for (int i = KERNEL_RADIUS; i < listSize - KERNEL_RADIUS; i++) {
			float newFov = 0.0f;
			for (int j = -KERNEL_RADIUS; j <= KERNEL_RADIUS; j++) {
				newFov += originalFovs.get(i + j) * GAUSSIAN_KERNEL[j + KERNEL_RADIUS];
			}
			flashPlus$allCameraKeyframes.get(i).put("fov", newFov);
		}
	}

	@Unique
	private static ModelPart flashPlus$getNamedModelPart(
			net.minecraft.client.model.EntityModel<?> model, String partName) {

		Class<?> currentClass = model.getClass();
		while (currentClass != null) {
			try {
				java.lang.reflect.Field field = currentClass.getDeclaredField(partName);
				field.setAccessible(true);
				Object part = field.get(model);
				return part instanceof ModelPart ? (ModelPart) part : null;
			} catch (NoSuchFieldException e) {
				currentClass = currentClass.getSuperclass();
			} catch (IllegalAccessException e) {
				System.err.println("[FlashPlus] Could not access field: " + partName);
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}

	@Unique
	private void flashPlus$saveDepthFrame(int width, int height, int frameIndex) {
		com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(width, height, false);

		float near = 0.05f;
		float far = Minecraft.getInstance().gameRenderer.getDepthFar();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float z = flashPlus$rawDepthBuffer.get(x + y * width);

				// Formula to turn logarithmic depth into linear distance
				float z_n = 2.0f * z - 1.0f;
				float linZ = (2.0f * near * far) / (far + near - z_n * (far - near));

				// Normalize for visual PNG (0 = near, 255 = far)
				float normalized = (linZ - near) / (far - near);
				int gray = (int) (java.lang.Math.clamp(normalized, 0, 1) * 255.0f);

				int col = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
				img.setPixel(x, height - 1 - y, col);
			}
		}
	}
	// Define the output path (adjust folder name as needed)
	String pathStr  = this.settings.output().toAbsolutePath().toString();
	int lastDot     = pathStr.lastIndexOf('.');
	String basePath = lastDot > 0 ? pathStr.substring(0, lastDot) : pathStr;
	File exportDir = new File(basePath, "flashplus_depth");
    if (!exportDir.()) exportDir.mkdirs();

	java.io.File outputFile = new java.io.File(exportDir, String.format("depth_frame_%04d.png", frameIndex));

	// Offload the file write to the IO executor so the render thread doesn't hang
    com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> {
		net.minecraft.Util.getIoWorkerExecutor().execute(() -> {
			try {
				img.writeTo(outputFile);
			} catch (java.io.IOException e) {
				// Replace with your logger if you have one
				System.err.println("Failed to save depth frame: " + e.getMessage());
			} finally {
				// CRITICAL: NativeImage uses off-heap memory; it must be closed manually
				img.close();
			}
		});
	});
}