package redsmods.flashplus.mixin.client.Exporting;

import com.moulberry.flashback.exporting.PerfectFrames;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.flashplus.depth.DepthFrames;

/**
 * Hooks into PerfectFrames.enable() and disable() to keep DepthFrames in
 * sync. All depth-capture state lives in DepthFrames — a plain non-mixin
 * class that is safe to reference from anywhere.
 */
@Mixin(value = PerfectFrames.class, remap = false)
public class PerfectFramesMixin {

    @Inject(method = "enable", at = @At("HEAD"), remap = false)
    private static void flashplus$onEnable(CallbackInfo ci) {
        DepthFrames.setCaptureDepth(false);
        DepthFrames.worldMatrix = new Matrix4f();
    }

    @Inject(method = "disable", at = @At("HEAD"), remap = false)
    private static void flashplus$onDisable(CallbackInfo ci) {
        DepthFrames.setCaptureDepth(false);
    }
}