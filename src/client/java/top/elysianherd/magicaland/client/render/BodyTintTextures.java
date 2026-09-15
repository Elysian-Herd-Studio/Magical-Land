package top.elysianherd.magicaland.client.render;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.cutiemark.CutieMarkData;

public final class BodyTintTextures {
    private static final Identifier SOURCE = new Identifier("magicaland", "textures/entity/base.png");
    private static final Logger LOGGER = LoggerFactory.getLogger("magicaland/body-shading");
    private static final int MAX_ENTRIES = 128;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final Map<Palette, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Draft[] DRAFTS = new Draft[2];
    private static NativeImage source;
    private static long tick, bytes;
    private static boolean initialized, failed;

    private BodyTintTextures() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "body_shading"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(BodyTintTextures::clear); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (!ModelManager.isEditing()) clearDrafts();
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

    /** Null selects the original texture and vertex multiply, including on resource/cache failure. */
    public static Identifier get(ModelConfig config, String partColor) {
        return get(config, partColor, null);
    }

    public static Identifier get(ModelConfig config, String partColor, String bone) {
        if (config == null || partColor == null || failed) return null;
        CutieMarkTexture.Side side = CutieMarkTexture.sideForBone(bone);
        String mark = side == null ? "" : config.cutieMarkData(side == CutieMarkTexture.Side.LEFT);
        if (mark.isEmpty()) side = null;
        boolean legacy = "legacy".equals(config.bodyShadingMode);
        if (legacy && side == null) return null;
        int base = BodyColorRamp.rgb(partColor);
        Palette key = new Palette(base, BodyPalette.shadow(config, base), BodyPalette.highlight(config, base), legacy, side, mark);
        if (side != null && ModelManager.isEditing() && config == ModelManager.getActiveModel())
            return getDraft(key);
        Entry existing = CACHE.get(key);
        if (existing != null) { existing.used = tick; return existing.id; }
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            loadSource();
            long cost = (long) source.getWidth() * source.getHeight() * 8;
            // Do not evict a texture while another model's buffered vertices can still reference it.
            if (CACHE.size() >= MAX_ENTRIES || bytes + cost > MAX_BYTES) return null;
            image = new NativeImage(NativeImage.Format.RGBA, source.getWidth(), source.getHeight(), false);
            fill(image, key);
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = client.getTextureManager().registerDynamicTexture("magicaland_body", texture);
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
            LOGGER.warn("Body palette unavailable; using legacy tint until resources reload", exception);
            return null;
        }
    }

    private static Identifier getDraft(Palette key) {
        int slot = key.side.ordinal();
        Draft previous = DRAFTS[slot];
        if (previous != null && key.equals(previous.key)) return previous.id;
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            loadSource();
            if (previous != null) {
                fill(previous.texture.getImage(), key);
                previous.texture.upload();
                previous.key = key;
                return previous.id;
            }
            long cost = (long) source.getWidth() * source.getHeight() * 8;
            if (bytes + cost > MAX_BYTES) return null;
            image = new NativeImage(NativeImage.Format.RGBA, source.getWidth(), source.getHeight(), false);
            fill(image, key);
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("magicaland_mark_draft", texture);
            texture.setFilter(false, false);
            texture.upload();
            DRAFTS[slot] = new Draft(registered, texture, key, cost);
            bytes += cost;
            return registered;
        } catch (Exception exception) {
            if (registered != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            else if (texture != null) texture.close();
            else if (image != null) image.close();
            failed = true;
            LOGGER.warn("Cutie mark preview unavailable until resources reload", exception);
            return null;
        }
    }

    private static void loadSource() throws java.io.IOException {
        if (source != null) return;
        try (InputStream input = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(SOURCE).getInputStream()) {
            source = NativeImage.read(NativeImage.Format.RGBA, input);
        }
        if ((long) source.getWidth() * source.getHeight() > 1024 * 1024) {
            source.close(); source = null;
            throw new IllegalArgumentException("Body shading supports textures up to 1 megapixel");
        }
    }

    private static void fill(NativeImage image, Palette key) {
        int[] ramp = key.legacy ? null : BodyColorRamp.lookup(key.base, key.shadow, key.highlight);
        int[] mark = key.side == null ? null : CutieMarkData.decode(key.mark);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int original = source.getColor(x, y);
            int color = key.legacy ? CutieMarkTexture.legacyTint(original, key.base) : BodyColorRamp.recolorAbgr(original, ramp);
            if (mark != null) {
                int index = CutieMarkTexture.pixelIndex(key.side, x, y, source.getWidth(), source.getHeight());
                if (index >= 0) color = CutieMarkTexture.composite(original, color, mark[index]);
            }
            image.setColor(x, y, color);
        }
    }

    public static String colorForBone(ModelConfig config, String name) {
        if (config == null) return null;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "body" -> config.bodyColor; case "neck" -> config.neckColor;
            case "head" -> config.headColor; case "nose" -> config.noseColor;
            case "leftear" -> config.leftEarColor; case "rightear" -> config.rightEarColor;
            case "horn" -> config.hornColor;
            default -> name.startsWith("LFront") || name.equalsIgnoreCase("LForeLeg") ? config.leftFrontLimbColor
                    : name.startsWith("RFront") || name.equalsIgnoreCase("RForeLeg") ? config.rightFrontLimbColor
                    : name.startsWith("LHind") ? config.leftHindLimbColor
                    : name.startsWith("RHind") ? config.rightHindLimbColor
                    : name.toLowerCase(Locale.ROOT).contains("wing") ? config.wingColor : null;
        };
    }

    private static void clear() {
        clearDrafts();
        for (Entry entry : CACHE.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
        CACHE.clear(); bytes = 0; failed = false;
        if (source != null) { source.close(); source = null; }
    }

    private static void clearDrafts() {
        for (int i = 0; i < DRAFTS.length; i++) if (DRAFTS[i] != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(DRAFTS[i].id);
            bytes -= DRAFTS[i].bytes;
            DRAFTS[i] = null;
        }
    }

    private record Palette(int base, int shadow, int highlight, boolean legacy, CutieMarkTexture.Side side, String mark) {}
    private static final class Draft {
        final Identifier id;
        final NativeImageBackedTexture texture;
        final long bytes;
        Palette key;
        Draft(Identifier id, NativeImageBackedTexture texture, Palette key, long bytes) {
            this.id = id; this.texture = texture; this.key = key; this.bytes = bytes;
        }
    }
    private static final class Entry {
        final Identifier id; final long bytes; long used;
        Entry(Identifier id, long used, long bytes) { this.id = id; this.used = used; this.bytes = bytes; }
    }
}
