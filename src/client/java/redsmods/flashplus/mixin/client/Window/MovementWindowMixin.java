package redsmods.flashplus.mixin.client.Window;

import com.moulberry.flashback.editor.ui.windows.MovementWindow;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static redsmods.flashplus.FlashplusClient.lockRoll;

@Mixin(MovementWindow.class)
public class MovementWindowMixin {

    @Inject(
            method = "render",
            at = @At(
                    value = "CONSTANT",
                    args = "stringValue=flashback.lock_pitch",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private static void addLockRollCheckbox(ImBoolean open, boolean newlyOpened, CallbackInfo ci) {
        ImGui.sameLine();
        if (ImGui.checkbox("Lock Roll", lockRoll)) {
            lockRoll = !lockRoll;
        }
    }
}