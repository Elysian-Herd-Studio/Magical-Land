package top.elysianherd.magicaland.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 仅在外部缓存未命中时读取资源，不执行第三方盔甲渲染器。 */
final class ArmorColorSource {
    record Source(String key, int[] abgr) {}
    private record SpriteKey(Identifier id, int tint) {}
    private static final Source MISSING = new Source("missing", new int[0]);
    private static final int MAX_SAMPLES = 4096, MAX_SPRITES = 8, MAX_BYTES = 8 * 1024 * 1024;

    private ArmorColorSource() {}

    static Source resolve(ItemStack stack, EquipmentSlot slot, BakedModel model) {
        if (stack == null || stack.isEmpty() || !wearable(slot)) return MISSING;
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        for (Identifier texture : wearingTextures(stack, slot)) {
            Source source = read(resources, texture, slot, null, 0xFFFFFF, MAX_SAMPLES);
            if (source.abgr().length > 0) return new Source("armor:" + slot + ":" + source.key(), source.abgr());
        }
        if (model == null) return MISSING;
        var sprites = new LinkedHashMap<SpriteKey, Sprite>();
        try {
            List<BakedQuad> quads = quads(model);
            Map<Integer, Integer> tints = tintMap(stack, quads);
            for (BakedQuad quad : quads) {
                int tint = quad.hasColor() ? tints.getOrDefault(quad.getColorIndex(), 0xFFFFFF) : 0xFFFFFF;
                addSprite(sprites, quad.getSprite(), tint);
            }
        } catch (RuntimeException | LinkageError unsupported) {
            // 动态模型不提供普通 quads 时，仍可尝试其公开粒子图。
        }
        var pixels = new ArrayList<Integer>();
        var key = new StringBuilder("item:");
        for (var entry : sprites.entrySet()) {
            Source source = sprite(resources, entry.getValue(), entry.getKey().tint());
            if (source.abgr().length == 0) continue;
            key.append(source.key()).append(';');
            for (int pixel : source.abgr()) pixels.add(pixel);
        }
        if (!pixels.isEmpty()) return new Source(key.toString(), pixels.stream().mapToInt(Integer::intValue).toArray());
        try { return sprite(resources, model.getParticleSprite(), 0xFFFFFF); }
        catch (RuntimeException | LinkageError unsupported) { return MISSING; }
    }

    /** 键只记录模型实际使用的 tint，不把耐久、名称等无关 NBT 塞进缓存。 */
    static List<Integer> tintKey(ItemStack stack, BakedModel model) {
        if (stack == null || stack.isEmpty() || model == null || stack.getItem() instanceof DyeableItem) return List.of();
        try {
            var key = new ArrayList<Integer>();
            tintMap(stack, quads(model)).forEach((index, rgb) -> { key.add(index); key.add(rgb); });
            return List.copyOf(key);
        } catch (RuntimeException | LinkageError unsupported) { return List.of(); }
    }

    private static Map<Integer, Integer> tintMap(ItemStack stack, List<BakedQuad> quads) {
        var tints = new TreeMap<Integer, Integer>();
        if (stack.getItem() instanceof DyeableItem) return tints;
        var provider = ColorProviderRegistry.ITEM.get(stack.getItem());
        for (BakedQuad quad : quads) if (quad.hasColor() && quad.getColorIndex() >= 0) {
            int index = quad.getColorIndex();
            if (tints.containsKey(index)) continue;
            if (tints.size() >= 16) throw new IllegalArgumentException("Too many item tint channels");
            int rgb = provider == null ? -1 : provider.getColor(stack, index);
            tints.put(index, rgb & 0xFFFFFF);
        }
        return tints;
    }

    private static List<BakedQuad> quads(BakedModel model) {
        var quads = new ArrayList<BakedQuad>();
        Random random = Random.create(42);
        for (int face = -1; face < Direction.values().length; face++) {
            random.setSeed(42);
            var found = model.getQuads(null, face < 0 ? null : Direction.values()[face], random);
            for (int index = 0; index < Math.min(32, found.size()); index++) quads.add(found.get(index));
        }
        return quads;
    }

    private static List<Identifier> wearingTextures(ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlotType() != slot) return List.of();
        try {
            String name = armor.getMaterial().getName();
            Identifier material = Identifier.tryParse(name);
            if (material == null) return List.of();
            var namespaces = new LinkedHashSet<String>();
            if (!name.contains(":")) namespaces.add(Registries.ITEM.getId(stack.getItem()).getNamespace());
            namespaces.add(material.getNamespace());
            String path = "textures/models/armor/" + material.getPath() + "_layer_" + (slot == EquipmentSlot.LEGS ? 2 : 1) + ".png";
            return namespaces.stream().map(namespace -> new Identifier(namespace, path)).toList();
        } catch (RuntimeException | LinkageError unsupported) { return List.of(); }
    }

    private static void addSprite(Map<SpriteKey, Sprite> sprites, Sprite sprite, int tint) {
        if (sprite == null || sprites.size() >= MAX_SPRITES) return;
        Identifier id = sprite.getContents().getId();
        if (!MissingSprite.getMissingSpriteId().equals(id)) sprites.putIfAbsent(new SpriteKey(id, tint), sprite);
    }

    private static Source sprite(ResourceManager resources, Sprite sprite, int tint) {
        try {
            if (sprite == null) return MISSING;
            var contents = sprite.getContents();
            Identifier id = contents.getId();
            if (MissingSprite.getMissingSpriteId().equals(id)) return MISSING;
            Identifier png = new Identifier(id.getNamespace(), "textures/" + id.getPath() + ".png");
            return read(resources, png, null, new SpriteDimensions(contents.getWidth(), contents.getHeight()),
                    tint, MAX_SAMPLES / MAX_SPRITES);
        } catch (RuntimeException | LinkageError unsupported) { return MISSING; }
    }

    /** 成功时调用方负责 close；先限制编码和解码尺寸，缺失或不支持时返回 null。 */
    static NativeImage readImage(ResourceManager manager, Identifier id) {
        try { return readImage(manager.getResource(id).orElse(null)); }
        catch (RuntimeException | LinkageError unsupported) { return null; }
    }

    private static NativeImage readImage(Resource resource) {
        if (resource == null) return null;
        try {
            byte[] bytes;
            try (var input = resource.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (!validPng(bytes)) return null;
            return NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
        } catch (Exception | LinkageError unsupported) { return null; }
    }

    private static Source read(ResourceManager resources, Identifier png, EquipmentSlot slot,
                               SpriteDimensions hint, int tint, int limit) {
        try {
            var resource = resources.getResource(png).orElse(null);
            if (resource == null) return MISSING;
            try (NativeImage image = readImage(resource)) {
                if (image == null) return MISSING;
                int width = hint == null ? image.getWidth() : hint.width();
                int height = hint == null ? image.getHeight() : hint.height();
                int[] first = {Integer.MIN_VALUE};
                var animation = resource.getMetadata().decode(AnimationResourceMetadata.READER).orElse(null);
                if (animation != null) {
                    var size = animation.getSize(image.getWidth(), image.getHeight());
                    if (hint == null) { width = size.width(); height = size.height(); }
                    animation.forEachFrame((index, time) -> { if (first[0] == Integer.MIN_VALUE) first[0] = index; });
                }
                if (width <= 0 || height <= 0 || image.getWidth() % width != 0 || image.getHeight() % height != 0) return MISSING;
                int columns = image.getWidth() / width, rows = image.getHeight() / height;
                int frame = first[0] == Integer.MIN_VALUE ? 0 : first[0];
                if (frame < 0 || frame >= columns * rows) return MISSING;
                int x0 = frame % columns * width, y0 = frame / columns * height;
                int sampleColumns = Math.min(width, Math.min(limit, (int) Math.ceil(Math.sqrt((double) limit * width / height))));
                int sampleRows = Math.min(height, Math.max(1, limit / sampleColumns));
                int[] pixels = new int[limit];
                int count = 0;
                // 每格确定性抖动采一点，避免高清细条纹与等距采样重合，也覆盖完整长条图。
                for (int row = 0; row < sampleRows; row++) {
                    for (int column = 0; column < sampleColumns; column++) {
                        int seed = row * sampleColumns + column;
                        int x = sampleCoordinate(column, width, sampleColumns, seed * 2);
                        int y = sampleCoordinate(row, height, sampleRows, seed * 2 + 1);
                        if (slot != null && !slotPixel(slot, (x + .5) * 64 / width, (y + .5) * 32 / height)) continue;
                        int pixel = image.getColor(x0 + x, y0 + y);
                        if ((pixel >>> 24) < 16) continue;
                        pixels[count++] = multiply(pixel, tint);
                    }
                }
                return count == 0 ? MISSING : new Source(png + "#" + frame + ":" + width + "x" + height + ":" + Integer.toHexString(tint), Arrays.copyOf(pixels, count));
            }
        } catch (Exception | LinkageError unsupported) { return MISSING; }
    }

    private static boolean validPng(byte[] bytes) {
        if (bytes.length < 24 || bytes.length > MAX_BYTES) return false;
        var header = ByteBuffer.wrap(bytes);
        if (header.getLong(0) != 0x89504E470D0A1A0AL || header.getInt(12) != 0x49484452) return false;
        int width = header.getInt(16), height = header.getInt(20);
        return width > 0 && height > 0 && width <= 4096 && height <= 4096 && (long) width * height <= 4194304;
    }

    private static int multiply(int abgr, int rgb) {
        int red = (abgr & 255) * (rgb >> 16 & 255) / 255;
        int green = (abgr >> 8 & 255) * (rgb >> 8 & 255) / 255;
        int blue = (abgr >> 16 & 255) * (rgb & 255) / 255;
        return abgr & 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private static int sampleCoordinate(int cell, int extent, int cells, int seed) {
        int low = cell * extent / cells, high = (cell + 1) * extent / cells;
        int hash = seed + 0x9E3779B9;
        hash = (hash ^ hash >>> 16) * 0x7FEB352D;
        hash = (hash ^ hash >>> 15) * 0x846CA68B;
        return low + Math.floorMod(hash ^ hash >>> 16, high - low);
    }

    private static boolean wearable(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static boolean slotPixel(EquipmentSlot slot, double x, double y) {
        return switch (slot) {
            case HEAD -> box(x, y, 0, 0, 8, 8, 8) || box(x, y, 32, 0, 8, 8, 8);
            case CHEST -> box(x, y, 16, 16, 8, 12, 4) || box(x, y, 40, 16, 4, 12, 4);
            case LEGS -> box(x, y, 16, 16, 8, 12, 4) || box(x, y, 0, 16, 4, 12, 4);
            case FEET -> box(x, y, 0, 16, 4, 12, 4);
            default -> false;
        };
    }

    private static boolean box(double x, double y, int u, int v, int width, int height, int depth) {
        return y >= v && y < v + depth && x >= u + depth && x < u + depth + 2 * width
                || y >= v + depth && y < v + depth + height && x >= u && x < u + 2 * (width + depth);
    }
}
