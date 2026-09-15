package top.elysianherd.magicaland.client.render;

/** Three painted colour stops; independent of Minecraft for deterministic testing. */
public final class BodyColorRamp {
    public static final int SHADOW_LEVEL = 224;
    public static final int BASE_LEVEL = 245;

    private BodyColorRamp() {}

    public static int rgb(String value) {
        if (value == null) return 0xFFFFFF;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6 && hex.length() != 8) return 0xFFFFFF;
        try { return (int) Long.parseLong(hex, 16) & 0xFFFFFF; }
        catch (NumberFormatException ignored) { return 0xFFFFFF; }
    }

    public static int automaticShadow(int base) {
        double[] hsv = hsv(base);
        if (hsv[1] > 0.02) {
            hsv[0] += hsv[0] < 90 || hsv[0] >= 280 ? -6 : 6;
            hsv[1] = Math.min(1, hsv[1] * 1.08);
        }
        hsv[2] *= 0.89;
        return fromHsv(hsv);
    }

    public static int automaticHighlight(int base) {
        double[] hsv = hsv(base);
        hsv[1] *= 0.90;
        hsv[2] += (1 - hsv[2]) * 0.04;
        return fromHsv(hsv);
    }

    public static int[] lookup(int base, int shadow, int highlight) {
        int[] result = new int[256];
        for (int v = 0; v < 256; v++) {
            if (v < SHADOW_LEVEL) result[v] = mix(0, shadow, v / (double) SHADOW_LEVEL);
            else if (v < BASE_LEVEL) result[v] = mix(shadow, base,
                    (v - SHADOW_LEVEL) / (double) (BASE_LEVEL - SHADOW_LEVEL));
            else result[v] = mix(base, highlight, (v - BASE_LEVEL) / (255.0 - BASE_LEVEL));
        }
        return result;
    }

    public static int recolor(int rgb, int[] ramp) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        // Leave already-coloured markings alone; transparent alpha is handled by the caller.
        if (max - min > 12) return rgb & 0xFFFFFF;
        int l = (54 * r + 183 * g + 19 * b + 128) >> 8;
        int mapped = ramp[l];
        return channel((mapped >> 16 & 255) + r - l) << 16
                | channel((mapped >> 8 & 255) + g - l) << 8
                | channel((mapped & 255) + b - l);
    }

    public static int recolorAbgr(int pixel, int[] ramp) {
        if ((pixel >>> 24) == 0) return pixel;
        int rgb = (pixel & 255) << 16 | (pixel >> 8 & 255) << 8 | (pixel >> 16 & 255);
        int mapped = recolor(rgb, ramp);
        return (pixel & 0xFF000000) | (mapped & 255) << 16 | (mapped & 0xFF00) | (mapped >> 16 & 255);
    }

    private static int mix(int a, int b, double t) {
        int r = channel((int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t));
        int g = channel((int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t));
        int blue = channel((int) Math.round((a & 255) * (1 - t) + (b & 255) * t));
        return r << 16 | g << 8 | blue;
    }

    private static int channel(int v) { return Math.max(0, Math.min(255, v)); }

    private static double[] hsv(int rgb) {
        double r = (rgb >> 16 & 255) / 255.0, g = (rgb >> 8 & 255) / 255.0, b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double h = d == 0 ? 0 : max == r ? (g - b) / d : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
        return new double[] { (h * 60 + 360) % 360, max == 0 ? 0 : d / max, max };
    }

    private static int fromHsv(double[] hsv) {
        double h = ((hsv[0] % 360) + 360) % 360 / 60, s = hsv[1], v = hsv[2];
        int sector = (int) h;
        double f = h - sector, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double[] rgb = switch (sector) {
            case 0 -> new double[] {v, t, p}; case 1 -> new double[] {q, v, p};
            case 2 -> new double[] {p, v, t}; case 3 -> new double[] {p, q, v};
            case 4 -> new double[] {t, p, v}; default -> new double[] {v, p, q};
        };
        return channel((int) Math.round(rgb[0] * 255)) << 16
                | channel((int) Math.round(rgb[1] * 255)) << 8 | channel((int) Math.round(rgb[2] * 255));
    }
}
