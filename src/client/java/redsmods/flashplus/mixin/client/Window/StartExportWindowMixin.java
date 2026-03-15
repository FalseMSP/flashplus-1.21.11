package redsmods.flashplus.mixin.client.Window;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.windows.StartExportWindow;
import imgui.moulberry90.ImGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static redsmods.flashplus.FlashplusClient.*;

@Mixin(value = StartExportWindow.class, remap = false)
public class StartExportWindowMixin {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Limgui/moulberry90/ImGui;dummy(FF)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private static void renderFlashplusOptions(CallbackInfo ci) {
        ImGuiHelper.separatorWithText("Flashplus Options");

        if (ImGui.checkbox("Camera Track", cjson)) {
            cjson = !cjson;
        }

        ImGui.sameLine();

        if (ImGui.checkbox("Entity Track", etjson)) {
            etjson = !etjson;
        }

        if (ImGui.checkbox("Use Quaternion", useQuaternion)) {
            useQuaternion = !useQuaternion;
        }

//        ImGui.sameLine();
    }

}