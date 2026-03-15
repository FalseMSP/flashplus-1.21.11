package redsmods.flashplus;

import net.fabricmc.api.ClientModInitializer;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlashplusClient implements ClientModInitializer {
	public static List<Map<String, Object>> trackedmodels = new ArrayList<Map<String, Object>>();
	public static boolean cjson = true;
	public static boolean etjson = true;
	public static boolean useQuaternion = true;
	public static boolean takePanorama = false;
	public static boolean deleteCubeMap = true;
	public static boolean lockRoll = false;

	public static float fov;
	public static double roll;
	public static Quaternionf quaternion;

	public static float getFOV() {
		return fov;
	}

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
	}
}