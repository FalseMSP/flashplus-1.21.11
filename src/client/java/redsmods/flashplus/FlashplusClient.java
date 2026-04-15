package redsmods.flashplus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Quaternionf;
import redsmods.flashplus.depth.DEPTHEXPORT;
import redsmods.flashplus.depth.DEPTHVISUALS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlashplusClient implements ClientModInitializer {

	// Runtime-only state (not persisted)
	public static List<Map<String, Object>> trackedmodels = new ArrayList<>();
	public static float fov;
	public static double roll;
	public static Quaternionf quaternion;
	public static int depthTickIndex = 0;

	private static Config config;

	public static Config getConfig() {
		return config;
	}

	public static float getFOV() {
		return fov;
	}

	@Override
	public void onInitializeClient() {
		Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("flashplus");
		try {
			Files.createDirectories(configFolder);
		} catch (IOException e) {
			// log error
		}
		config = Config.tryLoadFromFolder(configFolder);
	}
}