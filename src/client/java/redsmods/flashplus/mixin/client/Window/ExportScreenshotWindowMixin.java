package redsmods.flashplus.mixin.client.Window;

import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.windows.ExportScreenshotWindow;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.state.EditorState;
import imgui.moulberry90.ImGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static redsmods.flashplus.FlashplusClient.deleteCubeMap;
import static redsmods.flashplus.FlashplusClient.takePanorama;

@Mixin(ExportScreenshotWindow.class)
public class ExportScreenshotWindowMixin {

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    // This targets the specific checkbox call for "No GUI"
                    target = "Limgui/moulberry90/ImGui;checkbox(Ljava/lang/String;Z)Z",
                    ordinal = 1 // 0 is SSAA, 1 is No GUI
            )
    )
    private static void injectTakePanoramaCheckbox(CallbackInfo ci) {
        // Move to the right of the "No GUI" tooltip/checkbox
        ImGui.sameLine();

        // Render your checkbox
        if (ImGui.checkbox("Take Panorama", takePanorama)) {
            takePanorama = !takePanorama;
        }

        ImGuiHelper.tooltip("Captures a Equirectangular panorama");
        ImGui.sameLine();

        // Render your checkbox
        if (ImGui.checkbox("Delete CubeMap", deleteCubeMap)) {
            deleteCubeMap = !deleteCubeMap;
        }

        ImGuiHelper.tooltip("Deletes the cube map helpers");
    }

    @Inject(
            method = "lambda$render$0",
            // We target the ExportJob constructor call as the anchor
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/ExportJob;<init>(Lcom/moulberry/flashback/exporting/ExportSettings;)V"
            ),
            cancellable = true
    )
    private static void handlePanoramaExport(FlashbackConfigV1 config, EditorState editorState, String pathStr, CallbackInfo ci) {
        if (!takePanorama) return;

        // 1. Cancel the original single screenshot job
        ci.cancel();

        // 2. Re-calculate the variables that were previously captured
        Path path = Path.of(pathStr);
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        int tick = com.moulberry.flashback.Flashback.getReplayServer().getReplayTick();

        // Grab UI settings from the config
        boolean transparent = config.internalExport.transparentBackground && !editorState.replayVisuals.renderSky;
        boolean ssaa = config.internalExport.ssaa;
        boolean noGui = config.internalExport.noGui;

        // Resolution: Force standard square cubemap size
        int size = config.internalExport.resolution[0];

        // 3. Define the 6 faces
        Object[][] views = {
                {0f, 0f, "_front"},   // Forward
                {90f, 0f, "_right"},  // Right
                {180f, 0f, "_back"},  // Back
                {-90f, 0f, "_left"},  // Left
                {0f, 90f, "_down"},   // Down
                {0f, -90f, "_up"}     // Up
        };

        // 4. Queue the jobs
        for (Object[] view : views) {
            float yaw = (float) view[0];
            float pitch = (float) view[1];
            String suffix = (String) view[2];

            String fileName = path.getFileName().toString();
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
            Path facePath = path.getParent().resolve(baseName + suffix + ".png");

            ExportSettings settings = new ExportSettings(
                    null, editorState.copyWithoutKeyframes(),
                    player.position(), yaw, pitch,
                    size, size,
                    tick, tick, 1, false,
                    VideoContainer.PNG_SEQUENCE, null, null, 0,
                    transparent, ssaa, noGui,
                    false, false, null,
                    facePath, null
            );

            ExportJobQueue.queuedJobs.add(settings);
        }

        ExportJobQueue.drainingQueue = true;
        config.delayedSaveToDefaultFolder();
    }
}