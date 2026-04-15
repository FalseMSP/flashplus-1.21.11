package redsmods.flashplus.mixin.client.Window;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.windows.StartExportWindow;
import imgui.moulberry90.ImGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.flashplus.Config;
import redsmods.flashplus.FlashplusClient;
import redsmods.flashplus.depth.DEPTHEXPORT;
import redsmods.flashplus.depth.DEPTHVISUALS;

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
        Config config = FlashplusClient.getConfig();
        if (ImGui.checkbox("Camera Track", config.cjson)) {
            config.cjson = !config.cjson;
        }

        ImGui.sameLine();

        if (ImGui.checkbox("Entity Track", config.etjson)) {
            config.etjson = !config.etjson;
        }

        if (ImGui.checkbox("Use Quaternion", config.useQuaternion)) {
            config.useQuaternion = !config.useQuaternion;
        }

        ImGui.sameLine();

        if (ImGui.checkbox("Depth Export", config.depthexport)) {
            config.depthexport = !config.depthexport;
        }

        DEPTHVISUALS[] depthSettings = { DEPTHVISUALS.LEVELS, DEPTHVISUALS.ENTITIES, DEPTHVISUALS.PARTICLES };

        DEPTHEXPORT[] depthprecision = { DEPTHEXPORT.HIGHPRECISION, DEPTHEXPORT.NORMALPRECISION};

        if (config.depthexport) {

            ImGui.textWrapped("PSA: Depth is exported as a image sequence!!");

            DEPTHVISUALS newDepthInfo = ImGuiHelper.enumCombo("Depth Settings", config.depthinfo, depthSettings);

            if (newDepthInfo != config.depthinfo) {
                config.depthinfo = newDepthInfo;
            }

            DEPTHEXPORT newDepthexport = ImGuiHelper.enumCombo("Depth Precision", config.depthexports, depthprecision);

            if (newDepthexport != config.depthexports) {
                config.depthexports = newDepthexport;
                ImGui.textWrapped("Please restart minecraft for these changes to be applied");
            }
        }
//        ImGui.sameLine();
    }

}