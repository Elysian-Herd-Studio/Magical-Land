package top.elysianherd.magicaland.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;

public final class TransformationMessage {
    public static final long COOLDOWN_NANOS = 1_000_000_000L;
    private static final Set<String> APPEARANCE_FIELDS = Set.of(
            "frontManeStyle", "backManeStyle", "tailStyle", "eyeStyle", "irisColor", "irisLightColor",
            "irisLightColorLocked", "eyelashColor", "scleraColor", "pupilColor", "hornColor", "wingColor",
            "bodyColor", "bodyShadingMode", "bodyShadowColor", "bodyHighlightColor", "bodyShadowColorLocked",
            "bodyHighlightColorLocked", "neckColor", "headColor", "leftEarColor", "rightEarColor", "limbColor",
            "leftFrontLimbColor", "rightFrontLimbColor", "leftHindLimbColor", "rightHindLimbColor", "noseColor",
            "frontManeColor", "backManeColor", "tailColor", "maneShadingMode", "maneColorLinkVersion",
            "backManeColorLocked", "tailColorLocked", "frontManeShadowColor", "frontManeHighlightColor",
            "backManeShadowColor", "backManeHighlightColor", "tailShadowColor", "tailHighlightColor",
            "frontManeShadowColorLocked", "frontManeHighlightColorLocked", "backManeShadowColorLocked",
            "backManeHighlightColorLocked", "tailShadowColorLocked", "tailHighlightColorLocked", "maneDyeEnabled",
            "maneDyePreset", "maneDyeColor", "maneDyeAccentColor", "frontManeDyeColors", "backManeDyeColors",
            "tailDyeColors", "magicGlowColor", "showHorn", "showWings", "hornColorLocked", "wingColorLocked",
            "bodyColorLocked", "noseColorLocked", "neckColorLocked", "headColorLocked", "leftEarColorLocked",
            "rightEarColorLocked", "leftFrontLimbColorLocked", "rightFrontLimbColorLocked",
            "leftHindLimbColorLocked", "rightHindLimbColorLocked");

    private TransformationMessage() {}

    public static boolean requested(JsonObject message) {
        JsonElement value = message == null ? null : message.get("transform");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    public static boolean modelChanged(String previous, String next) {
        if (next == null || next.equals(previous)) return false;
        try {
            JsonElement parsedNext = JsonParser.parseString(next);
            if (!parsedNext.isJsonObject()) return false;
            JsonObject nextAppearance = appearance(parsedNext.getAsJsonObject());
            if (previous == null) return !nextAppearance.entrySet().isEmpty();
            JsonElement parsedPrevious = JsonParser.parseString(previous);
            if (!parsedPrevious.isJsonObject()) return true;
            return !appearance(parsedPrevious.getAsJsonObject()).equals(nextAppearance);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static JsonObject appearance(JsonObject model) {
        JsonObject appearance = new JsonObject();
        // 新外观字段需加入此表；名称、未知元数据和 JSON 排版不能刷光尘。
        for (String field : APPEARANCE_FIELDS) {
            JsonElement value = model.get(field);
            if (value != null && !value.isJsonNull()) appearance.add(field, value);
        }
        return appearance;
    }

    public static boolean mayBroadcast(JsonObject message, String previous, String next,
            Long lastTransformation, long now) {
        return requested(message) && modelChanged(previous, next)
                && (lastTransformation == null || now - lastTransformation >= COOLDOWN_NANOS);
    }
}
