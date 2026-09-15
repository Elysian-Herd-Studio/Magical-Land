package top.elysianherd.magicaland.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.Reader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Config {
    public static final String DEFAULT_MGL_SKIN_URL = "https://skin.mgland.top/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File BASE_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "magicaland");
    private static final File CONFIG_FILE = new File(BASE_DIR, "config.json");

    public boolean replacePlayerModel = true;
    public boolean firstPersonMagicGlow = true;
    public boolean magicSounds = true;
    public boolean automaticGaze = true;
    public boolean broadcastOwnModel = true;
    public String mainMenuPonyButton = "all";
    public String magicGlowStyle = "current";
    public String previewLighting = "balanced";
    
    public String activeModelName = "";
    public String mglSkinUrl = DEFAULT_MGL_SKIN_URL;
    public String mglSkinToken = "";
    public String mglSkinUsername = "";

    private static Config instance;

    public static Config getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (!BASE_DIR.exists()) {
            BASE_DIR.mkdirs();
        }
        if (CONFIG_FILE.exists()) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                instance = GSON.fromJson(reader, Config.class);
                if (instance == null) {
                    instance = new Config();
                    save();
                }
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
                instance = new Config();
                save();
            }
        } else {
            instance = new Config();
            save();
        }
    }

    public static void save() {
        if (instance == null) {
            instance = new Config();
        }
        if (!BASE_DIR.exists()) {
            BASE_DIR.mkdirs();
        }
        try {
            writeAtomically(CONFIG_FILE, writer -> GSON.toJson(instance, writer));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeAtomically(File file, WriterConsumer writerConsumer) throws IOException {
        Path target = file.toPath();
        Path temporary = target.resolveSibling(file.getName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writerConsumer.write(writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    private interface WriterConsumer {
        void write(Writer writer);
    }
}
