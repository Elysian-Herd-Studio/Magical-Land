package top.elysianherd.magicaland.client.render;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.render.ManeDye.TextureKey;

public final class ManeTintTextures {
    private static final Identifier SOURCE = new Identifier("magicaland", "textures/entity/mane.png");
    private static final Logger LOGGER = LoggerFactory.getLogger("magicaland/mane-shading");
    private static final int MAX_ENTRIES = 128;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final Map<TextureKey, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static NativeImage source;
    private static final Map<String, ManeDyeMask> DYE_MASKS = new HashMap<>();
    private static final Set<String> FAILED_MASKS = new HashSet<>();
    private static long tick, bytes;
    private static boolean initialized, failed;

    private ManeTintTextures() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "mane_shading"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(ManeTintTextures::clear); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            Iterator<Entry> entries = CACHE.values().iterator();
            while (entries.hasNext()) {
                Entry entry = entries.next();
                if (tick - entry.used > 100 || ((CACHE.size() >= MAX_ENTRIES * 3 / 4 || bytes >= MAX_BYTES * 3 / 4) && tick - entry.used > 5)) {
                    client.getTextureManager().destroyTexture(entry.id);
                    bytes -= entry.bytes;
                    entries.remove();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());
    }

    public static Identifier get(ModelConfig config, String boneName) {
        ManePalette.Part part = ManePalette.partForBone(boneName);
        if (config == null || part == null || failed) return null;
        boolean legacy = "legacy".equals(config.maneShadingMode), requested = ManeDye.enabled(config, part);
        if (legacy && !requested) return null;
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (source == null) {
                try (InputStream input = client.getResourceManager().getResourceOrThrow(SOURCE).getInputStream()) {
                    source = NativeImage.read(NativeImage.Format.RGBA, input);
                }
                if ((long) source.getWidth() * source.getHeight() > 1024 * 1024) {
                    source.close(); source = null;
                    throw new IllegalArgumentException("Mane shading supports textures up to 1 megapixel");
                }
            }
            ManeDyeMask mask = requested ? mask(client, ManeDye.maskName(config, part), part,
                    source.getWidth(), source.getHeight()) : null;
            if (legacy && mask == null) return null;
            TextureKey key = ManeDye.textureKey(config, part, mask != null, legacy);
            List<ManePalette.Colors> colors = key.colors();
            Entry existing = CACHE.get(key);
            if (existing != null) { existing.used = tick; return existing.id; }
            long cost = (long) source.getWidth() * source.getHeight() * 8;
            // 本帧已进入顶点缓冲的纹理不能立即淘汰。
            if (CACHE.size() >= MAX_ENTRIES || bytes + cost > MAX_BYTES) return null;
            int[] bases = colors.stream().mapToInt(ManePalette.Colors::base).toArray();
            int[][] ramps = colors.stream().map(c -> BodyColorRamp.lookup(c.base(), c.shadow(), c.highlight())).toArray(int[][]::new);
            image = new NativeImage(NativeImage.Format.RGBA, source.getWidth(), source.getHeight(), false);
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int channel = mask == null ? 0 : mask.channel(part, x, y, source.getWidth(), source.getHeight());
                image.setColor(x, y, ManeDye.recolor(source.getColor(x, y), channel, legacy, bases, ramps));
            }
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = client.getTextureManager().registerDynamicTexture("magicaland_mane", texture);
            texture.setFilter(false, false);
            texture.upload();
            CACHE.put(key, new Entry(registered, tick, cost));
            bytes += cost;
            return registered;
        } catch (Exception exception) {
            if (registered != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            else if (texture != null) texture.close();
            else if (image != null) image.close();
            failed = true;
            LOGGER.warn("Mane palette unavailable; using legacy tint until resources reload", exception);
            return null;
        }
    }

    private static ManeDyeMask mask(MinecraftClient client, String name, ManePalette.Part part, int width, int height) {
        if (name == null || FAILED_MASKS.contains(name)) return null;
        try {
            ManeDyeMask dyeMask = DYE_MASKS.get(name);
            if (dyeMask == null) {
                Identifier resource = new Identifier("magicaland", "mane_dyes/" + name + ".json");
                try (InputStream input = client.getResourceManager().getResourceOrThrow(resource).getInputStream()) {
                    byte[] data = input.readNBytes(262145);
                    if (data.length > 262144) throw new IllegalArgumentException("Mane dye mask exceeds 256 KiB");
                    dyeMask = ManeDyeMask.read(new StringReader(new String(data, StandardCharsets.UTF_8)), name);
                }
            }
            if (!dyeMask.compatible(width, height)) throw new IllegalArgumentException("Mane dye mask aspect ratio mismatch");
            DYE_MASKS.put(name, dyeMask);
            return dyeMask.hasPart(part) ? dyeMask : null;
        } catch (Exception exception) {
            FAILED_MASKS.add(name); DYE_MASKS.remove(name);
            LOGGER.warn("Mane dye mask {} unavailable; using plain hair for this style until resources reload", name, exception);
            return null;
        }
    }

    private static void clear() {
        for (Entry entry : CACHE.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
        CACHE.clear(); bytes = 0; failed = false;
        DYE_MASKS.clear(); FAILED_MASKS.clear();
        if (source != null) { source.close(); source = null; }
    }

    private static final class Entry {
        final Identifier id; final long bytes; long used;
        Entry(Identifier id, long used, long bytes) { this.id = id; this.used = used; this.bytes = bytes; }
    }
}
