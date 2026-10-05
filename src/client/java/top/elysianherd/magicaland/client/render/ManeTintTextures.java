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
    private static final Map<CacheKey, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static NativeImage source;
    private static final Map<String, ManeDyeMask> DYE_MASKS = new HashMap<>();
    private static final Set<String> FAILED_MASKS = new HashSet<>();
    private static long tick, bytes;
    private static boolean initialized, failed, surfaceFailed;

    private ManeTintTextures() {}

    public record Surface(Identifier texture, int columns, int rows, int[] slots) {}
    private record CacheKey(TextureKey palette, boolean surface) {}
    record SurfacePalette(List<ManePalette.Colors> colors, int[] slots, int columns, int rows) {}

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
            loadSource(client);
            ManeDyeMask mask = requested ? mask(client, ManeDye.maskName(config, part), part,
                    source.getWidth(), source.getHeight()) : null;
            if (legacy && mask == null) return null;
            TextureKey paletteKey = ManeDye.textureKey(config, part, mask != null, legacy);
            CacheKey key = new CacheKey(paletteKey, false);
            List<ManePalette.Colors> colors = paletteKey.colors();
            Entry existing = CACHE.get(key);
            if (existing != null) { existing.used = tick; return existing.id; }
            long cost = (long) source.getWidth() * source.getHeight() * 8;
            // 本帧已进入顶点缓冲的纹理不能立即淘汰。
            if (!reserve(client, cost)) return null;
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

    static SurfacePalette surfacePalette(ModelConfig config, ManePalette.Part part) {
        boolean legacy = "legacy".equals(config.maneShadingMode);
        List<ManePalette.Colors> regions = ManeDye.palette(config, part, true, legacy);
        List<ManePalette.Colors> unique = new java.util.ArrayList<>();
        int[] slots = new int[ManeDye.REGION_COUNT];
        for (int i = 0; i < slots.length; i++) {
            ManePalette.Colors color = regions.get(i + 1);
            int slot = unique.indexOf(color);
            if (slot < 0) { slot = unique.size(); unique.add(color); }
            slots[i] = slot;
        }
        int columns = Math.min(3, unique.size()), rows = (unique.size() + columns - 1) / columns;
        return new SurfacePalette(List.copyOf(unique), slots, columns, rows);
    }

    public static Surface getSurface(ModelConfig config, String boneName) {
        ManePalette.Part part = ManePalette.partForBone(boneName);
        if (config == null || part == null || failed || surfaceFailed || !ManeDye.enabled(config, part)) return null;
        boolean legacy = "legacy".equals(config.maneShadingMode);
        SurfacePalette palette = surfacePalette(config, part);
        List<ManePalette.Colors> unique = palette.colors();
        int[] slots = palette.slots();
        int columns = palette.columns(), rows = palette.rows();
        CacheKey key = new CacheKey(new TextureKey(unique, legacy, null, null), true);
        Entry existing = CACHE.get(key);
        if (existing != null) {
            existing.used = tick;
            return new Surface(existing.id, columns, rows, slots);
        }
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            loadSource(client);
            int width = source.getWidth(), height = source.getHeight();
            long cost = (long) width * height * columns * rows * 8;
            if (!reserve(client, cost)) return null;
            int[] bases = unique.stream().mapToInt(ManePalette.Colors::base).toArray();
            int[][] ramps = unique.stream().map(c -> BodyColorRamp.lookup(c.base(), c.shadow(), c.highlight())).toArray(int[][]::new);
            image = new NativeImage(NativeImage.Format.RGBA, width * columns, height * rows, false);
            for (int tile = 0; tile < columns * rows; tile++) {
                int channel = Math.min(tile, unique.size() - 1), left = tile % columns * width, top = tile / columns * height;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                    image.setColor(left + x, top + y, ManeDye.recolor(source.getColor(x, y), channel, legacy, bases, ramps));
            }
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = client.getTextureManager().registerDynamicTexture("magicaland_mane_surface", texture);
            texture.setFilter(false, false);
            texture.upload();
            CACHE.put(key, new Entry(registered, tick, cost));
            bytes += cost;
            return new Surface(registered, columns, rows, slots);
        } catch (Exception exception) {
            if (registered != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            else if (texture != null) texture.close();
            else if (image != null) image.close();
            surfaceFailed = true;
            LOGGER.warn("Mane surface palette unavailable; using texture masks until resources reload", exception);
            return null;
        }
    }

    private static void loadSource(MinecraftClient client) throws java.io.IOException {
        if (source != null) return;
        try (InputStream input = client.getResourceManager().getResourceOrThrow(SOURCE).getInputStream()) {
            source = NativeImage.read(NativeImage.Format.RGBA, input);
        }
        if ((long) source.getWidth() * source.getHeight() > 1024 * 1024) {
            source.close(); source = null;
            throw new IllegalArgumentException("Mane shading supports textures up to 1 megapixel");
        }
    }

    private static boolean reserve(MinecraftClient client, long cost) {
        if (cost > MAX_BYTES) return false;
        Iterator<Entry> entries = CACHE.values().iterator();
        while ((CACHE.size() >= MAX_ENTRIES || bytes + cost > MAX_BYTES) && entries.hasNext()) {
            Entry entry = entries.next();
            if (entry.used >= tick) continue;
            client.getTextureManager().destroyTexture(entry.id);
            bytes -= entry.bytes;
            entries.remove();
        }
        return CACHE.size() < MAX_ENTRIES && bytes + cost <= MAX_BYTES;
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
        CACHE.clear(); bytes = 0; failed = false; surfaceFailed = false;
        DYE_MASKS.clear(); FAILED_MASKS.clear();
        ManeSurfaceMasks.clear();
        if (source != null) { source.close(); source = null; }
    }

    private static final class Entry {
        final Identifier id; final long bytes; long used;
        Entry(Identifier id, long used, long bytes) { this.id = id; this.used = used; this.bytes = bytes; }
    }
}
