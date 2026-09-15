package top.elysianherd.magicaland.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.EnumMap;

/** 每款发型独立的分色区域。0 为未覆盖，1–6 为可独立调色的区域。 */
public final class ManeDyeMask {
    private final int width, height;
    private final String preset;
    private final EnumMap<ManePalette.Part, byte[]> channels;

    private ManeDyeMask(int width, int height, String preset, EnumMap<ManePalette.Part, byte[]> channels) {
        this.width = width;
        this.height = height;
        this.preset = preset;
        this.channels = channels;
    }

    public static ManeDyeMask read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        int version = integer(root.get("version"));
        String preset = root.get("preset").getAsString();
        String style = ManeDye.maskStyle(preset);
        if ((version < 1 || version > 3) || style == null || (version < 3 && !"01".equals(style)))
            throw new IllegalArgumentException("Unsupported mane dye mask");
        int width = integer(root.get("texture_width")), height = integer(root.get("texture_height"));
        if (width < 1 || height < 1 || width > 1024 || height > 1024)
            throw new IllegalArgumentException("Invalid mask dimensions");
        EnumMap<ManePalette.Part, byte[]> channels = new EnumMap<>(ManePalette.Part.class);
        JsonArray regions = root.getAsJsonArray("regions");
        if (regions.size() > (version == 3 ? 18 : 4)) throw new IllegalArgumentException("Too many mask regions");
        int[] seen = new int[ManePalette.Part.values().length];
        int runCount = 0;
        for (JsonElement element : regions) {
            JsonObject region = element.getAsJsonObject();
            ManePalette.Part part = ManePalette.Part.valueOf(region.get("part").getAsString());
            if (!ManeDye.supportsStyle(style, part)) throw new IllegalArgumentException("Mask part unavailable for this style");
            int channel = integer(region.get("channel"));
            if (channel < 1 || channel > (version == 3 ? ManeDye.REGION_COUNT : 2)
                    || (version < 3 && channel == 2 && (version < 2 || part != ManePalette.Part.BACK))
                    || (seen[part.ordinal()] & 1 << channel) != 0)
                throw new IllegalArgumentException("Duplicate part or unsupported dye channel");
            seen[part.ordinal()] |= 1 << channel;
            byte[] mask = channels.computeIfAbsent(part, key -> new byte[width * height]);
            for (JsonElement row : region.getAsJsonArray("runs")) {
                if (++runCount > 8192) throw new IllegalArgumentException("Too many mask spans");
                JsonArray run = row.getAsJsonArray();
                if (run.size() != 3) throw new IllegalArgumentException("Invalid mask span");
                int y = integer(run.get(0)), x0 = integer(run.get(1)), x1 = integer(run.get(2));
                if (y < 0 || y >= height || x0 < 0 || x1 > width || x1 <= x0)
                    throw new IllegalArgumentException("Mask span outside atlas");
                for (int x = x0; x < x1; x++) {
                    if (mask[y * width + x] != 0) throw new IllegalArgumentException("Overlapping spans");
                    mask[y * width + x] = (byte) (version < 3 ? (channel == 1 ? 4 : 5) : channel);
                }
            }
        }
        return new ManeDyeMask(width, height, preset, channels);
    }

    public static ManeDyeMask read(Reader reader, String expectedPreset) {
        ManeDyeMask mask = read(reader);
        if (!mask.preset.equals(expectedPreset)) throw new IllegalArgumentException("Mane dye mask identity mismatch");
        return mask;
    }

    public String preset() { return preset; }

    public boolean hasPart(ManePalette.Part part) { return channels.containsKey(part); }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Mask coordinate must be an integer");
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid mask coordinate");
        return (int) number;
    }

    public boolean compatible(int sourceWidth, int sourceHeight) {
        return sourceWidth > 0 && sourceHeight > 0 && (long) sourceWidth * height == (long) sourceHeight * width;
    }

    public boolean contains(ManePalette.Part part, int x, int y, int sourceWidth, int sourceHeight) {
        return channel(part, x, y, sourceWidth, sourceHeight) != 0;
    }

    public int channel(ManePalette.Part part, int x, int y, int sourceWidth, int sourceHeight) {
        if (!compatible(sourceWidth, sourceHeight) || x < 0 || y < 0 || x >= sourceWidth || y >= sourceHeight) return 0;
        byte[] mask = channels.get(part);
        if (mask == null) return 0;
        int u = (int) (((long) x * 2 + 1) * width / (2L * sourceWidth));
        int v = (int) (((long) y * 2 + 1) * height / (2L * sourceHeight));
        return mask[v * width + u];
    }
}
