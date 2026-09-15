package top.elysianherd.magicaland.client.render;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

final class PonyArmorTextures {
    private static final Map<Identifier, Identifier> NEUTRAL = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();
    private static boolean initialized;

    private PonyArmorTextures() {}

    static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "armor_colors"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(PonyArmorTextures::clear); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());
    }

    static Identifier neutral(Identifier source) {
        Identifier cached = NEUTRAL.get(source);
        if (cached != null) return cached;
        if (FAILED.contains(source) || NEUTRAL.size() >= 8) return null;
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            var client = MinecraftClient.getInstance();
            try (var input = client.getResourceManager().getResourceOrThrow(source).getInputStream()) {
                image = NativeImage.read(NativeImage.Format.RGBA, input);
            }
            if (image.getWidth() > 1024 || image.getHeight() > 1024) throw new IllegalArgumentException("Armor texture too large");
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                image.setColor(x, y, neutralPixel(image.getColor(x, y)));
            }
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = client.getTextureManager().registerDynamicTexture("magicaland_armor", texture);
            texture.setFilter(false, false);
            texture.upload();
            NEUTRAL.put(source, registered);
            return registered;
        } catch (Exception failure) {
            if (registered != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            else if (texture != null) texture.close();
            else if (image != null) image.close();
            FAILED.add(source);
            org.slf4j.LoggerFactory.getLogger("Magical-Land/Armor").warn("盔甲贴图调色暂不可用，保留原贴图", failure);
            return null;
        }
    }

    static int neutralPixel(int abgr) {
        int value = Math.max(abgr & 255, Math.max(abgr >> 8 & 255, abgr >> 16 & 255));
        return abgr & 0xFF000000 | value << 16 | value << 8 | value;
    }

    private static void clear() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        NEUTRAL.values().forEach(manager::destroyTexture);
        NEUTRAL.clear();
        FAILED.clear();
    }
}
