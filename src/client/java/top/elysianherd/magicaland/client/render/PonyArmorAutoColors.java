package top.elysianherd.magicaland.client.render;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 未适配的盔甲保留自己的配色，借用小马盔甲的形状和手绘明暗。 */
final class PonyArmorAutoColors {
    private static final Identifier DEFINITION = new Identifier("magicaland", "textures/armor/templates/metal.json");
    static final Identifier FALLBACK = new Identifier("magicaland", "textures/armor/iron.png");
    private static final Logger LOGGER = LoggerFactory.getLogger("Magical-Land/Armor");
    private static final int MAX_ENTRIES = 256, MAX_WORK_PER_TICK = 8;
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final Map<SourceKey, SourceEntry> SOURCES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<ArmorPalette.Palette, Entry> TEXTURES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Item, Long> MODEL_RETRY = new LinkedHashMap<>(16, 0.75f, true);
    private static ArmorColorTemplate template;
    private static long tick, bytes;
    private static int sampled, generated;
    private static boolean initialized, templateFailed, warned;

    private PonyArmorAutoColors() {}

    static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "armor_auto_colors"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(PonyArmorAutoColors::clear); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            sampled = generated = 0;
            Iterator<Entry> entries = TEXTURES.values().iterator();
            while (entries.hasNext()) {
                Entry entry = entries.next();
                if (tick - entry.used > 100 || ((TEXTURES.size() >= MAX_ENTRIES * 3 / 4 || bytes >= MAX_BYTES * 3 / 4)
                        && tick - entry.used > 5)) {
                    client.getTextureManager().destroyTexture(entry.id);
                    bytes -= entry.bytes;
                    entries.remove();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());
    }

    static Identifier get(ItemStack stack, ArmorItem armor, AbstractClientPlayerEntity player) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            SourceKey wearing = new SourceKey(stack.getItem(), armor.getSlotType(), null, List.of(), true);
            SourceKey key = wearing;
            SourceEntry source = SOURCES.get(key);
            if (source == null) {
                BakedModel model = model(client, stack, player);
                key = new SourceKey(stack.getItem(), armor.getSlotType(), model, ArmorColorSource.tintKey(stack, model), false);
                source = SOURCES.get(key);
                if (source == null) {
                    if (sampled >= MAX_WORK_PER_TICK) return FALLBACK;
                    sampled++;
                    ArmorColorSource.Source pixels = ArmorColorSource.resolve(stack, armor.getSlotType(), model);
                    source = new SourceEntry(ArmorPalette.extract(pixels.abgr()));
                    if (pixels.key().startsWith("armor:")) key = wearing;
                    if (SOURCES.size() >= MAX_ENTRIES) SOURCES.remove(SOURCES.keySet().iterator().next());
                    SOURCES.put(key, source);
                }
            }
            ArmorPalette.Palette palette = source.palette;
            if (armor instanceof DyeableItem dyeable && dyeable.hasColor(stack)) {
                int dye = dyeable.getColor(stack) & 0xFFFFFF;
                palette = new ArmorPalette.Palette(dye,
                        palette == null || palette.accent() == palette.body() ? dye : palette.accent());
            }
            return palette == null ? FALLBACK : texture(palette);
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
            return FALLBACK;
        }
    }

    private static BakedModel model(MinecraftClient client, ItemStack stack, AbstractClientPlayerEntity player) {
        Long retry = MODEL_RETRY.get(stack.getItem());
        if (retry != null && tick < retry) return null;
        try {
            BakedModel model = client.getItemRenderer().getModel(stack, player.getWorld(), player, player.getId());
            MODEL_RETRY.remove(stack.getItem());
            return model;
        } catch (RuntimeException | LinkageError failure) {
            if (MODEL_RETRY.size() >= MAX_ENTRIES) MODEL_RETRY.remove(MODEL_RETRY.keySet().iterator().next());
            MODEL_RETRY.put(stack.getItem(), tick + 20);
            warn(failure);
            // 物品预览坏了仍可读取正常穿戴图，失败查询短暂退避。
            return null;
        }
    }

    private static Identifier texture(ArmorPalette.Palette palette) {
        Entry cached = TEXTURES.get(palette);
        if (cached != null) { cached.used = tick; return cached.id; }
        if (templateFailed || generated >= MAX_WORK_PER_TICK) return FALLBACK;
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            loadTemplate();
            long cost = (long) template.width() * template.height() * 8;
            // 还未提交的绘制可能引用旧图，渲染途中不驱逐或改写它。
            if (TEXTURES.size() >= MAX_ENTRIES || bytes + cost > MAX_BYTES) return FALLBACK;
            generated++;
            int[] pixels = template.paint(palette);
            image = new NativeImage(NativeImage.Format.RGBA, template.width(), template.height(), false);
            for (int y = 0; y < template.height(); y++) for (int x = 0; x < template.width(); x++)
                image.setColor(x, y, pixels[y * template.width() + x]);
            texture = new NativeImageBackedTexture(image);
            image = null;
            var manager = MinecraftClient.getInstance().getTextureManager();
            registered = manager.registerDynamicTexture("magicaland_auto_armor", texture);
            texture.setFilter(false, false);
            texture.upload();
            TEXTURES.put(palette, new Entry(registered, tick, cost));
            bytes += cost;
            return registered;
        } catch (Exception failure) {
            if (registered != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            else if (texture != null) texture.close();
            else if (image != null) image.close();
            templateFailed = true;
            warn(failure);
            return FALLBACK;
        }
    }

    private static void loadTemplate() throws IOException {
        if (template != null) return;
        var resources = MinecraftClient.getInstance().getResourceManager();
        byte[] json;
        try (var input = resources.getResourceOrThrow(DEFINITION).getInputStream()) {
            json = input.readNBytes(65537);
        }
        if (json.length > 65536) throw new IOException("Armor template metadata too large");
        var definition = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        Identifier id = Identifier.tryParse(definition.get("texture").getAsString());
        if (id == null) throw new IOException("Invalid armor template texture");
        try (NativeImage image = ArmorColorSource.readImage(resources, id)) {
            if (image == null) throw new IOException("Missing armor template texture");
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                pixels[y * image.getWidth() + x] = image.getColor(x, y);
            template = ArmorColorTemplate.parse(definition, image.getWidth(), image.getHeight(), pixels);
        }
    }

    private static void warn(Throwable failure) {
        if (warned) return;
        warned = true;
        LOGGER.warn("盔甲取色遇到异常，使用可用贴图或基础外观", failure);
    }

    private static void clear() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        for (Entry entry : TEXTURES.values()) manager.destroyTexture(entry.id);
        TEXTURES.clear();
        SOURCES.clear();
        MODEL_RETRY.clear();
        template = null;
        bytes = 0;
        sampled = generated = 0;
        templateFailed = warned = false;
    }

    private record SourceKey(Item item, EquipmentSlot slot, BakedModel model, List<Integer> tints, boolean wearing) {}
    private record SourceEntry(ArmorPalette.Palette palette) {}
    private static final class Entry {
        final Identifier id;
        final long bytes;
        long used;
        Entry(Identifier id, long used, long bytes) { this.id = id; this.used = used; this.bytes = bytes; }
    }
}
