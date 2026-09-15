package top.elysianherd.magicaland.client.render;

import java.util.Iterator;
import java.util.LinkedHashMap;
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
import org.slf4j.LoggerFactory;
import top.elysianherd.magicaland.client.config.ModelConfig;

public final class EyeTintTextures {
    private static final Identifier SOURCE = new Identifier("magicaland", "textures/entity/base.png");
    private static final int MAX_ENTRIES = 64;
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private static final Map<Palette, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static NativeImage source;
    private static long tick, bytes;
    private static boolean initialized, failed;

    private EyeTintTextures() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "iris_colors"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(EyeTintTextures::clear); }
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

    public static Identifier get(ModelConfig config, boolean pupil) {
        if (config == null || failed) return null;
        Palette key = new Palette(IrisPalette.base(config), IrisPalette.light(config),
                BodyColorRamp.rgb(pupil ? config.pupilColor : config.eyelashColor), BodyColorRamp.rgb(config.scleraColor));
        if (key.base == IrisPalette.DEFAULT_BASE && key.light == IrisPalette.DEFAULT_LIGHT
                && key.black == 0 && key.sclera == 0xFFFFFF) return null;
        Entry existing = CACHE.get(key);
        if (existing != null) { existing.used = tick; return existing.id; }
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (source == null) {
                try (var input = client.getResourceManager().getResourceOrThrow(SOURCE).getInputStream()) {
                    source = NativeImage.read(NativeImage.Format.RGBA, input);
                }
                if (source.getWidth() != source.getHeight() || source.getWidth() % 256 != 0 || source.getWidth() > 1024) {
                    source.close(); source = null;
                    throw new IllegalArgumentException("Eye tint needs the existing square UV layout at a multiple of 256 pixels, up to 1024");
                }
            }
            long cost = (long) source.getWidth() * source.getHeight() * 8;
            // 不在渲染中途销毁其他玩家尚在顶点缓冲里引用的贴图。
            if (CACHE.size() >= MAX_ENTRIES || bytes + cost > MAX_BYTES) return null;
            image = new NativeImage(NativeImage.Format.RGBA, source.getWidth(), source.getHeight(), false);
            image.copyFrom(source);
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                image.setColor(x, y, EyePalette.recolorAbgr(source.getColor(x, y), x, y, source.getWidth(), source.getHeight(),
                        key.base, key.light, key.black, key.sclera));
            }
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = client.getTextureManager().registerDynamicTexture("magicaland_iris", texture);
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
            LoggerFactory.getLogger(EyeTintTextures.class).warn("虹膜调色暂不可用，资源重载前保留原眼色", exception);
            return null;
        }
    }

    private static void clear() {
        for (Entry entry : CACHE.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
        CACHE.clear(); bytes = 0; failed = false;
        if (source != null) { source.close(); source = null; }
    }

    private record Palette(int base, int light, int black, int sclera) {}
    private static final class Entry {
        final Identifier id; final long bytes; long used;
        Entry(Identifier id, long used, long bytes) { this.id = id; this.used = used; this.bytes = bytes; }
    }
}
