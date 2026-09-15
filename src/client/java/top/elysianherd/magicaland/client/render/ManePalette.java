package top.elysianherd.magicaland.client.render;

import java.util.Locale;
import top.elysianherd.magicaland.client.config.ModelConfig;

public final class ManePalette {
    public enum Part { FRONT, BACK, TAIL }
    public record Colors(int base, int shadow, int highlight) {}

    private ManePalette() {}

    public static Part partForBone(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("tail")) return Part.TAIL;
        if (lower.contains("backmane")) return Part.BACK;
        return lower.contains("mane") ? Part.FRONT : null;
    }

    public static boolean linked(ModelConfig c, Part part) {
        return part == Part.BACK ? c.backManeColorLocked : part == Part.TAIL && c.tailColorLocked;
    }

    public static int base(ModelConfig c, Part part) {
        if (linked(c, part)) part = Part.FRONT;
        return BodyColorRamp.rgb(switch (part) {
            case FRONT -> c.frontManeColor;
            case BACK -> c.backManeColor;
            case TAIL -> c.tailColor;
        });
    }

    public static boolean stopLocked(ModelConfig c, Part part, boolean highlight) {
        if (linked(c, part)) part = Part.FRONT;
        return switch (part) {
            case FRONT -> highlight ? c.frontManeHighlightColorLocked : c.frontManeShadowColorLocked;
            case BACK -> highlight ? c.backManeHighlightColorLocked : c.backManeShadowColorLocked;
            case TAIL -> highlight ? c.tailHighlightColorLocked : c.tailShadowColorLocked;
        };
    }

    public static int stop(ModelConfig c, Part part, boolean highlight) {
        if (linked(c, part)) part = Part.FRONT;
        if (stopLocked(c, part, highlight)) return automatic(base(c, part), highlight);
        return BodyColorRamp.rgb(switch (part) {
            case FRONT -> highlight ? c.frontManeHighlightColor : c.frontManeShadowColor;
            case BACK -> highlight ? c.backManeHighlightColor : c.backManeShadowColor;
            case TAIL -> highlight ? c.tailHighlightColor : c.tailShadowColor;
        });
    }

    public static int automatic(int base, boolean highlight) {
        return highlight ? BodyColorRamp.automaticHighlight(base) : BodyColorRamp.automaticShadow(base);
    }

    public static Colors colors(ModelConfig c, Part part) {
        return new Colors(base(c, part), stop(c, part, false), stop(c, part, true));
    }

    public static String colorForBone(ModelConfig c, String name) {
        Part part = partForBone(name);
        return c == null || part == null ? null : BodyPalette.hex(base(c, part));
    }

    public static void setBase(ModelConfig c, Part part, String color) {
        if (linked(c, part)) return;
        switch (part) {
            case FRONT -> c.frontManeColor = color;
            case BACK -> c.backManeColor = color;
            case TAIL -> c.tailColor = color;
        }
    }

    public static void setLinked(ModelConfig c, Part part, boolean locked) {
        if (part == Part.FRONT) return;
        if (linked(c, part) && !locked) {
            Colors visible = colors(c, part);
            boolean shadowLocked = stopLocked(c, part, false), highlightLocked = stopLocked(c, part, true);
            setLinkFlag(c, part, false);
            setBase(c, part, BodyPalette.hex(visible.base));
            setStopValue(c, part, false, BodyPalette.hex(visible.shadow));
            setStopValue(c, part, true, BodyPalette.hex(visible.highlight));
            setStopFlag(c, part, false, shadowLocked);
            setStopFlag(c, part, true, highlightLocked);
        }
        setLinkFlag(c, part, locked);
    }

    public static void setStopLocked(ModelConfig c, Part part, boolean highlight, boolean locked) {
        if (linked(c, part)) return;
        if (stopLocked(c, part, highlight) && !locked) {
            setStopValue(c, part, highlight, BodyPalette.hex(stop(c, part, highlight)));
        }
        setStopFlag(c, part, highlight, locked);
    }

    public static void setStop(ModelConfig c, Part part, boolean highlight, String color) {
        if (!linked(c, part) && !stopLocked(c, part, highlight)) setStopValue(c, part, highlight, color);
    }

    public static void resetAutomatic(ModelConfig c) {
        for (Part part : Part.values()) {
            setStopFlag(c, part, false, true);
            setStopFlag(c, part, true, true);
        }
    }

    private static void setLinkFlag(ModelConfig c, Part part, boolean locked) {
        if (part == Part.BACK) c.backManeColorLocked = locked;
        else if (part == Part.TAIL) c.tailColorLocked = locked;
    }

    private static void setStopFlag(ModelConfig c, Part part, boolean highlight, boolean locked) {
        switch (part) {
            case FRONT -> { if (highlight) c.frontManeHighlightColorLocked = locked; else c.frontManeShadowColorLocked = locked; }
            case BACK -> { if (highlight) c.backManeHighlightColorLocked = locked; else c.backManeShadowColorLocked = locked; }
            case TAIL -> { if (highlight) c.tailHighlightColorLocked = locked; else c.tailShadowColorLocked = locked; }
        }
    }

    private static void setStopValue(ModelConfig c, Part part, boolean highlight, String color) {
        switch (part) {
            case FRONT -> { if (highlight) c.frontManeHighlightColor = color; else c.frontManeShadowColor = color; }
            case BACK -> { if (highlight) c.backManeHighlightColor = color; else c.backManeShadowColor = color; }
            case TAIL -> { if (highlight) c.tailHighlightColor = color; else c.tailShadowColor = color; }
        }
    }
}
