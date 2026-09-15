package top.elysianherd.magicaland.cutiemark;

import java.util.Base64;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 固定 12×12、硬透明的 RGBA 数据；不接收图片文件或外部地址。 */
public final class CutieMarkData {
    public static final int SIZE = 12;
    public static final int PIXELS = SIZE * SIZE;
    public static final int BYTE_LENGTH = PIXELS * 4;
    public static final int ENCODED_LENGTH = BYTE_LENGTH / 3 * 4;

    private CutieMarkData() {}

    public static int[] decode(String data) {
        if (data == null || data.isEmpty()) return new int[PIXELS];
        if (data.length() != ENCODED_LENGTH) throw new IllegalArgumentException("Invalid cutie mark length");
        byte[] bytes = Base64.getDecoder().decode(data);
        if (bytes.length != BYTE_LENGTH) throw new IllegalArgumentException("Invalid cutie mark size");
        int[] pixels = new int[PIXELS];
        for (int i = 0; i < PIXELS; i++) {
            int offset = i * 4, alpha = bytes[offset + 3] & 255;
            if (alpha != 0 && alpha != 255) throw new IllegalArgumentException("Cutie marks use hard transparency");
            if (alpha != 0) pixels[i] = 0xFF000000 | (bytes[offset] & 255) << 16
                    | (bytes[offset + 1] & 255) << 8 | bytes[offset + 2] & 255;
        }
        return pixels;
    }

    public static String encode(int[] pixels) {
        if (pixels == null || pixels.length != PIXELS) throw new IllegalArgumentException("Expected 144 pixels");
        byte[] bytes = new byte[BYTE_LENGTH];
        boolean visible = false;
        for (int i = 0; i < PIXELS; i++) {
            int pixel = pixels[i], alpha = pixel >>> 24;
            if (alpha != 0 && alpha != 255) throw new IllegalArgumentException("Cutie marks use hard transparency");
            if (alpha == 0) continue;
            visible = true;
            int offset = i * 4;
            bytes[offset] = (byte) (pixel >> 16);
            bytes[offset + 1] = (byte) (pixel >> 8);
            bytes[offset + 2] = (byte) pixel;
            bytes[offset + 3] = (byte) 255;
        }
        return visible ? Base64.getEncoder().encodeToString(bytes) : "";
    }

    public static String sanitize(String data) {
        try { return encode(decode(data)); }
        catch (IllegalArgumentException ignored) { return ""; }
    }

    public static int[] mirror(int[] pixels) {
        if (pixels == null || pixels.length != PIXELS) throw new IllegalArgumentException("Expected 144 pixels");
        int[] mirrored = new int[PIXELS];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            mirrored[y * SIZE + x] = pixels[y * SIZE + SIZE - 1 - x];
        return mirrored;
    }

    public static boolean isValidModel(JsonObject model) {
        JsonElement linked = model.get("cutieMarkLinked");
        if (linked != null && (!linked.isJsonPrimitive() || !linked.getAsJsonPrimitive().isBoolean())) return false;
        if (!validField(model.get("cutieMarkLeft")) || !validField(model.get("cutieMarkRight"))) return false;
        return linked != null && !linked.getAsBoolean() || !model.has("cutieMarkRight")
                || model.get("cutieMarkRight").getAsString().isEmpty();
    }

    private static boolean validField(JsonElement value) {
        if (value == null) return true;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        String data = value.getAsString();
        try { return encode(decode(data)).equals(data); }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
