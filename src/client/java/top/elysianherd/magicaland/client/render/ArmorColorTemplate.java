package top.elysianherd.magicaland.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** 模板明暗不变，只替换主体和宝石色；固定区域保留原像素。 */
final class ArmorColorTemplate {
    private final int width, height;
    private final int[] source;
    private final byte[] channels;

    private ArmorColorTemplate(int width, int height, int[] source, byte[] channels) {
        this.width = width;
        this.height = height;
        this.source = source.clone();
        this.channels = channels;
    }

    static ArmorColorTemplate parse(JsonObject definition, int width, int height, int[] pixels) {
        if (definition.get("version").getAsInt() != 2 || width < 1 || height < 1
                || (long) width * height > 1024 * 1024 || pixels.length != width * height)
            throw new IllegalArgumentException("Invalid armor template");
        JsonObject shading = definition.getAsJsonObject("shading");
        if (shading.get("shadow_level").getAsInt() != BodyColorRamp.SHADOW_LEVEL
                || shading.get("base_level").getAsInt() != BodyColorRamp.BASE_LEVEL
                || shading.get("highlight_level").getAsInt() != 255
                || !"magicaland:geo/armor/metal.geo.json".equals(definition.get("geometry").getAsString()))
            throw new IllegalArgumentException("Unsupported armor shading or geometry");
        int logicalWidth = definition.get("texture_width").getAsInt();
        int logicalHeight = definition.get("texture_height").getAsInt();
        if (logicalWidth < 1 || logicalHeight < 1 || logicalWidth > 256 || logicalHeight > 256
                || !"body".equals(definition.get("default_channel").getAsString()))
            throw new IllegalArgumentException("Invalid armor template dimensions or channel");
        byte[] mask = new byte[logicalWidth * logicalHeight];
        JsonArray regions = definition.getAsJsonArray("regions");
        if (regions.size() > 64) throw new IllegalArgumentException("Too many armor regions");
        for (var element : regions) {
            JsonObject region = element.getAsJsonObject();
            if (!"accent".equals(region.get("channel").getAsString()))
                throw new IllegalArgumentException("Unknown armor channel");
            JsonArray runs = region.getAsJsonArray("runs");
            if (runs.size() > 4096) throw new IllegalArgumentException("Too many armor mask rows");
            for (var row : runs) {
                JsonArray run = row.getAsJsonArray();
                if (run.size() != 3) throw new IllegalArgumentException("Invalid armor mask row");
                int y = run.get(0).getAsInt(), left = run.get(1).getAsInt(), right = run.get(2).getAsInt();
                fill(mask, logicalWidth, logicalHeight, left, y, right, y + 1, (byte) 1);
            }
        }
        JsonArray fixed = definition.getAsJsonArray("fixed_regions");
        if (fixed.size() > 64) throw new IllegalArgumentException("Too many fixed armor regions");
        for (var element : fixed) {
            JsonArray rect = element.getAsJsonObject().getAsJsonArray("rect");
            if (rect.size() != 4) throw new IllegalArgumentException("Invalid fixed armor region");
            fill(mask, logicalWidth, logicalHeight, rect.get(0).getAsInt(), rect.get(1).getAsInt(),
                    rect.get(2).getAsInt(), rect.get(3).getAsInt(), (byte) 2);
        }
        byte[] scaled = new byte[pixels.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            scaled[y * width + x] = mask[(y * logicalHeight / height) * logicalWidth + x * logicalWidth / width];
        return new ArmorColorTemplate(width, height, pixels, scaled);
    }

    private static void fill(byte[] mask, int width, int height, int left, int top, int right, int bottom, byte value) {
        if (left < 0 || top < 0 || right > width || bottom > height || left >= right || top >= bottom)
            throw new IllegalArgumentException("Armor region outside template");
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            if (mask[y * width + x] != 0) throw new IllegalArgumentException("Overlapping armor channels");
            mask[y * width + x] = value;
        }
    }

    int width() { return width; }
    int height() { return height; }

    int[] paint(ArmorPalette.Palette palette) {
        int[] body = ramp(palette.body()), accent = ramp(palette.accent());
        int[] result = source.clone();
        for (int i = 0; i < result.length; i++) {
            if (channels[i] != 2) result[i] = BodyColorRamp.recolorAbgr(source[i], channels[i] == 1 ? accent : body);
        }
        return result;
    }

    private static int[] ramp(int base) {
        return BodyColorRamp.lookup(base, BodyColorRamp.automaticShadow(base), BodyColorRamp.automaticHighlight(base));
    }
}
