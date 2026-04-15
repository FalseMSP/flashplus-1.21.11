package redsmods.flashplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import redsmods.flashplus.depth.DEPTHEXPORT;
import redsmods.flashplus.depth.DEPTHVISUALS;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {

    // Defaults
    public boolean cjson = true;
    public boolean etjson = true;
    public boolean useQuaternion = true;
    public boolean takePanorama = false;
    public boolean deleteCubeMap = true;
    public boolean lockRoll = false;
    public boolean depthexport = true;
    public DEPTHVISUALS depthinfo = DEPTHVISUALS.LEVELS;
    public DEPTHEXPORT depthexports = DEPTHEXPORT.HIGHPRECISION;

    private static final String CONFIG_FILENAME = "flashplus.json";
    private transient Path loadedFromFolder = null;
    private transient int delayedSaveTicks = -1;
    // --- Singleton ---

    private static Config INSTANCE;

    public static Config get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("flashplus.json");
    }

    public static void load() {
        File file = configPath().toFile();
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                INSTANCE = GSON.fromJson(reader, Config.class);
            } catch (IOException e) {
                System.err.println("[FlashPlus] Failed to load config, using defaults: " + e.getMessage());
                INSTANCE = new Config();
            }
        } else {
            INSTANCE = new Config();
            save();
        }
    }

    public static void save() {
        try (Writer writer = new FileWriter(configPath().toFile())) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            System.err.println("[FlashPlus] Failed to save config: " + e.getMessage());
        }
    }

    public static Config tryLoadFromFolder(Path folder) {
        Path configFile = folder.resolve(CONFIG_FILENAME);

        if (Files.exists(configFile)) {
            try {
                Config config = GSON.fromJson(Files.readString(configFile), Config.class);
                if (config != null) {
                    config.loadedFromFolder = folder;
                    return config;
                }
            } catch (Exception e) {
                System.err.println("[FlashPlus] Failed to load config, using defaults: " + e.getMessage());
            }
        }

        Config config = new Config();
        config.loadedFromFolder = folder;
        config.saveToDefaultFolder();
        return config;
    }

    public void saveToDefaultFolder() {
        if (loadedFromFolder == null) {
            loadedFromFolder = FabricLoader.getInstance().getConfigDir().resolve("flashplus");
        }
        try {
            Files.createDirectories(loadedFromFolder);
            Files.writeString(loadedFromFolder.resolve(CONFIG_FILENAME), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[FlashPlus] Failed to save config: " + e.getMessage());
        }
    }

    public void delayedSaveToDefaultFolder() {
        delayedSaveTicks = 20;
    }

    public void tickDelayedSave() {
        if (delayedSaveTicks < 0) return;
        if (--delayedSaveTicks == 0) {
            saveToDefaultFolder();
            delayedSaveTicks = -1;
        }
    }
}