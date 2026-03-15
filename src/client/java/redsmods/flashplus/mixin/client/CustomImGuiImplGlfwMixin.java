package redsmods.flashplus.mixin.client;

import com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomImGuiImplGlfw.class)
public class CustomImGuiImplGlfwMixin {

    @Inject(method = "keyCallback", at = @At("HEAD"), cancellable = true, remap = false)
    private void suppressRollKeys(long windowId, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (key != GLFW.GLFW_KEY_Q && key != GLFW.GLFW_KEY_E) return;
        if (action == GLFW.GLFW_RELEASE) return; // always let releases through to avoid stuck keys

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null || !editorState.replayVisuals.overrideRoll) return;

        if (!ReplayUI.isActive()) return;
        if (ReplayUI.isMovingCamera()) return; // grabbed state — game should handle it
        if (ReplayUI.getIO().getWantCaptureKeyboard()) return; // imgui text field active, don't intercept

        // Eat the event — ImGui's isKeyDown still works via GLFW state polling,
        // so your ReplayUIRollMixin will see the key held without Minecraft getting it.
        ci.cancel();
    }
}