package redsmods.flashplus.mixin.client.Window;

import com.moulberry.flashback.editor.ui.windows.SelectedEntityPopup;
import com.moulberry.flashback.state.EditorState;
import imgui.moulberry90.ImGui;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.flashplus.FlashplusClient;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = SelectedEntityPopup.class, remap = false)
public class SelectedEntityPopupMixin {

    @Unique
    private static int flashPlus$selectedListIndex = -1;

    @Unique
    private static int flashPlus$currentComboSelection = 0;

    @Unique
    private static final String[] flashPlus$comboOptions = {
            "Eyes", "BlockPosition", "head", "rightArm", "leftArm",
            "body", "leftLeg", "rightLeg", "hat"
    };

    @Unique
    private static boolean flashPlus$showTrackEntityWindow = false;

    /**
     * Inject the Track Entity button after the audio source buttons,
     * right before the voice chat mute checkbox
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/loader/api/FabricLoader;isModLoaded(Ljava/lang/String;)Z",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private static void flashPlus$injectTrackEntityButton(
            Entity entity,
            EditorState editorState,
            CallbackInfo ci) {

        // Add "Open Track Entity" button
        if (ImGui.button("Open Track Entity")) {
            flashPlus$showTrackEntityWindow = !flashPlus$showTrackEntityWindow;
        }

        // Render the Track Entity window if open
        if (flashPlus$showTrackEntityWindow) {
            flashPlus$renderTrackEntityWindow(entity);
        }
    }

    @Unique
    private static void flashPlus$renderTrackEntityWindow(Entity entity) {
        ImGui.begin("Track Entity");

        // Combo Box for selecting which part to track
        if (ImGui.beginCombo("Track Mode", flashPlus$comboOptions[flashPlus$currentComboSelection])) {
            for (int i = 0; i < flashPlus$comboOptions.length; i++) {
                boolean selected = flashPlus$currentComboSelection == i;
                if (ImGui.selectable(flashPlus$comboOptions[i], selected)) {
                    flashPlus$currentComboSelection = i;
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        // Add button to track the selected part
        if (ImGui.button("Add Track")) {
            String selectedOption = flashPlus$comboOptions[flashPlus$currentComboSelection];
            String key = entity.getUUID().toString() + "/" + selectedOption;

            // Check if already exists
            boolean alreadyExists = false;
            for (Map<String, Object> existingMap : FlashplusClient.trackedmodels) {
                if (existingMap.containsKey(key)) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                Map<String, Object> newModelDict = new HashMap<>();
                newModelDict.put(key, selectedOption);
                FlashplusClient.trackedmodels.add(newModelDict);
                System.out.println("[FlashPlus] Added tracked entity part: " + key);
            } else {
                System.out.println("[FlashPlus] Entity part already tracked: " + key);
            }
        }

        ImGui.sameLine();

        // Remove selected track
        if (ImGui.button("Remove Selected")) {
            if (flashPlus$selectedListIndex >= 0 &&
                    flashPlus$selectedListIndex < FlashplusClient.trackedmodels.size()) {

                Map<String, Object> removed = FlashplusClient.trackedmodels.remove(flashPlus$selectedListIndex);
                if (!removed.isEmpty()) {
                    String removedKey = removed.keySet().iterator().next();
                    System.out.println("[FlashPlus] Removed tracked entity part: " + removedKey);
                }
                flashPlus$selectedListIndex = -1;
            }
        }

        ImGui.sameLine();

        // Clear all tracks
        if (ImGui.button("Clear All")) {
            FlashplusClient.trackedmodels.clear();
            flashPlus$selectedListIndex = -1;
            System.out.println("[FlashPlus] Cleared all tracked entities");
        }

        // List box showing currently tracked entity parts
        ImGui.textUnformatted("Tracked Entity Parts:");

        if (ImGui.beginListBox("##TrackedList", -1, 200)) {
            for (int i = 0; i < FlashplusClient.trackedmodels.size(); i++) {
                Map<String, Object> currentMap = FlashplusClient.trackedmodels.get(i);

                if (!currentMap.isEmpty()) {
                    String fullKey = currentMap.keySet().iterator().next();
                    boolean isSelected = flashPlus$selectedListIndex == i;

                    // Parse the key to show entity UUID and part name
                    String[] parts = fullKey.split("/");
                    String displayText = parts.length == 2
                            ? parts[1] + " (" + parts[0].substring(0, 8) + "...)"
                            : fullKey;

                    if (ImGui.selectable(displayText, isSelected)) {
                        flashPlus$selectedListIndex = i;
                    }
                }
            }
            ImGui.endListBox();
        }

        // Show info about current entity
        ImGui.separator();
        ImGui.textUnformatted("Current Entity UUID:");
        ImGui.textUnformatted(entity.getUUID().toString());

        ImGui.end();
    }
}