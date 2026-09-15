package top.elysianherd.magicaland.client.render;

import java.awt.Color;
import top.elysianherd.magicaland.client.config.ModelConfig;

public final class IrisPalette {
    public static final int DEFAULT_BASE = 0x516BD1;
    public static final int DEFAULT_LIGHT = 0x7BA1D2;

    private IrisPalette() {}

    public static int base(ModelConfig config) {
        return config == null ? DEFAULT_BASE : BodyColorRamp.rgb(config.irisColor);
    }

    public static int automaticLight(int base) {
        return remap(DEFAULT_LIGHT, DEFAULT_BASE, base);
    }

    public static int light(ModelConfig config) {
        return config == null ? DEFAULT_LIGHT : config.irisLightColorLocked
                ? automaticLight(base(config)) : BodyColorRamp.rgb(config.irisLightColor);
    }

    public static void setLightLocked(ModelConfig config, boolean locked) {
        if (config.irisLightColorLocked && !locked) config.irisLightColor = BodyPalette.hex(light(config));
        config.irisLightColorLocked = locked;
    }

    public static void reset(ModelConfig config) {
        config.irisColor = BodyPalette.hex(DEFAULT_BASE);
        config.irisLightColor = BodyPalette.hex(DEFAULT_LIGHT);
        config.irisLightColorLocked = true;
    }

    /** 128×128 逻辑图集中的两个独立区域，按像素中心适配等比例贴图。 */
    public static int region(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x >= width || y >= height) return 0;
        double u = (x + 0.5) * 128 / width, v = (y + 0.5) * 128 / height;
        if (u >= 2 && u < 4 && v >= 64 && v < 67) return 1;
        if (u >= 1.5 && u < 2 && v >= 63.5 && v < 64) return 2;
        return 0;
    }

    public static int recolorAbgr(int pixel, int region, int base, int light) {
        if ((pixel >>> 24) == 0 || (region != 1 && region != 2)) return pixel;
        int rgb = (pixel & 255) << 16 | (pixel & 0xFF00) | (pixel >>> 16 & 255);
        int mapped = remap(rgb, region == 1 ? DEFAULT_BASE : DEFAULT_LIGHT, region == 1 ? base : light);
        return (pixel & 0xFF000000) | (mapped & 255) << 16 | (mapped & 0xFF00) | (mapped >>> 16 & 255);
    }

    private static int remap(int source, int reference, int target) {
        if (target == reference) return source;
        float[] original = hsv(source), anchor = hsv(reference), selected = hsv(target);
        float hue = selected[0] + original[0] - anchor[0];
        float saturation = Math.min(1, selected[1] * original[1] / anchor[1]);
        // 极暗色也留下少量层次；其余颜色保留原渐变的明度比例和细微色相变化。
        float value = Math.min(1, Math.max(12f / 255f, selected[2]) * original[2] / anchor[2]);
        return Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF;
    }

    private static float[] hsv(int rgb) {
        return Color.RGBtoHSB(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, null);
    }
}
