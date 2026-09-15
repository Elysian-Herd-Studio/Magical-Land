package top.elysianherd.magicaland.client.render;

import top.elysianherd.magicaland.client.config.ModelConfig;

/** Resolves independent automatic/manual colour stops without mutating saved colours. */
public final class BodyPalette {
    private BodyPalette() {}

    public static int shadow(ModelConfig config, int base) {
        return !config.bodyShadowColorLocked && base == BodyColorRamp.rgb(config.bodyColor)
                ? BodyColorRamp.rgb(config.bodyShadowColor) : BodyColorRamp.automaticShadow(base);
    }

    public static int highlight(ModelConfig config, int base) {
        return !config.bodyHighlightColorLocked && base == BodyColorRamp.rgb(config.bodyColor)
                ? BodyColorRamp.rgb(config.bodyHighlightColor) : BodyColorRamp.automaticHighlight(base);
    }

    public static void setShadowLocked(ModelConfig config, boolean locked) {
        if (config.bodyShadowColorLocked && !locked) {
            config.bodyShadowColor = hex(BodyColorRamp.automaticShadow(BodyColorRamp.rgb(config.bodyColor)));
        }
        config.bodyShadowColorLocked = locked;
    }

    public static void setHighlightLocked(ModelConfig config, boolean locked) {
        if (config.bodyHighlightColorLocked && !locked) {
            config.bodyHighlightColor = hex(BodyColorRamp.automaticHighlight(BodyColorRamp.rgb(config.bodyColor)));
        }
        config.bodyHighlightColorLocked = locked;
    }

    public static void resetAutomatic(ModelConfig config) {
        config.bodyShadowColorLocked = true;
        config.bodyHighlightColorLocked = true;
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
