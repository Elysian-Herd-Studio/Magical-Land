package top.elysianherd.magicaland.client.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 稳定表情 ID、作者指定的动作搭配，以及不同眼型的骨骼适配。 */
public final class PonyExpressions {
    public static final Map<String, String> CHANNELS = Map.of(
            "normal", "CommonFace", "happy", "Smeile", "angry", "Angry",
            "closed", "close", "scrunched", "ScrunchedEyes", "left_pupil", "leye", "right_pupil", "reye");
    private static volatile Catalog catalog = loadBundled();

    public record Expression(String id, String name, String animation, boolean automaticGaze) {}
    public record EyeGaze(float outward, float up, float down) {}
    public record GazeLimits(float horizontal, float up, float down, EyeGaze left, EyeGaze right) {
        public GazeLimits(float horizontal, float up, float down) {
            this(horizontal, up, down, new EyeGaze(horizontal, up, down), new EyeGaze(horizontal, up, down));
        }
        public EyeGaze eye(boolean isLeft) { return isLeft ? left : right; }
    }
    public record EyeStyle(String id, Map<String, String> bones, GazeLimits gazeLimits) {}
    private record Catalog(Map<String, Expression> expressions, Map<String, RawAnimation> animations,
                           Map<String, RawAnimation> actions, Set<String> gazeActions,
                           Map<String, EyeStyle> styles, Set<String> styleBones) {}

    private PonyExpressions() {}

    public static RawAnimation forExpression(String id) {
        Catalog current = catalog;
        return id == null ? current.animations.get("neutral")
                : current.animations.getOrDefault(id, current.animations.get("neutral"));
    }

    public static RawAnimation forAction(String action) {
        Catalog current = catalog;
        return action == null ? current.animations.get("neutral")
                : current.actions.getOrDefault(action, current.animations.get("neutral"));
    }

    public static Map<String, Expression> expressions() { return catalog.expressions; }
    public static Map<String, EyeStyle> eyeStyles() { return catalog.styles; }

    public static boolean allowsAutomaticGaze(String action) {
        return action != null && catalog.gazeActions.contains(action);
    }

    public static GazeLimits gazeLimits(String styleId) {
        Catalog current = catalog;
        EyeStyle style = styleId == null ? current.styles.get("01")
                : current.styles.getOrDefault(styleId, current.styles.get("01"));
        return style.gazeLimits;
    }

    public static boolean shouldRender(String boneName, String styleId) {
        Catalog current = catalog;
        if (!current.styleBones.contains(boneName)) return true;
        EyeStyle style = styleId == null ? current.styles.get("01")
                : current.styles.getOrDefault(styleId, current.styles.get("01"));
        return style.bones.containsValue(boneName);
    }

    public static void reload(Reader reader) {
        catalog = parse(reader);
    }

    private static Catalog loadBundled() {
        try (var input = PonyExpressions.class.getResourceAsStream("/assets/magicaland/expressions.json")) {
            if (input == null) throw new IllegalStateException("缺少内置 expressions.json");
            return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("无法读取内置表情库", e);
        }
    }

    private static Catalog parse(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        if (json.get("version").getAsInt() != 1) throw new IllegalArgumentException("不支持的表情库版本");
        Map<String, Expression> definitions = new HashMap<>();
        Map<String, RawAnimation> animations = new HashMap<>();
        for (var entry : json.getAsJsonObject("expressions").entrySet()) {
            String id = entry.getKey();
            if (!id.matches("[a-z][a-z0-9_]{0,47}")) throw new IllegalArgumentException("无效的表情 ID: " + id);
            JsonObject value = entry.getValue().getAsJsonObject();
            String animation = value.get("animation").getAsString();
            if (!animation.matches("face\\.[a-z][a-z0-9_]{0,47}")) throw new IllegalArgumentException("无效的表情动画: " + animation);
            String gaze = value.has("gaze") ? value.get("gaze").getAsString() : "locked";
            if (!Set.of("auto", "locked", "off").contains(gaze)) throw new IllegalArgumentException("无效注视模式: " + gaze);
            definitions.put(id, new Expression(id, value.get("name").getAsString(), animation, "auto".equals(gaze)));
            animations.put(id, RawAnimation.begin().thenLoop(animation));
        }
        if (!definitions.containsKey("neutral")) throw new IllegalArgumentException("表情库必须包含 neutral");
        Map<String, RawAnimation> actions = new HashMap<>();
        Set<String> gazeActions = new HashSet<>();
        for (var entry : json.getAsJsonObject("actions").entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                actions.put(entry.getKey(), require(animations, value.getAsString()));
                if (require(definitions, value.getAsString()).automaticGaze) gazeActions.add(entry.getKey());
            } else {
                JsonObject sequence = value.getAsJsonObject();
                Expression first = require(definitions, sequence.get("expression").getAsString());
                Expression next = require(definitions, sequence.get("then_loop").getAsString());
                actions.put(entry.getKey(), RawAnimation.begin().thenPlay(first.animation).thenLoop(next.animation));
                if (first.automaticGaze && next.automaticGaze) gazeActions.add(entry.getKey());
            }
        }
        Map<String, EyeStyle> styles = new HashMap<>();
        Set<String> styleBones = new HashSet<>(CHANNELS.values());
        Map<String, String> boneRoles = new HashMap<>();
        CHANNELS.forEach((role, bone) -> boneRoles.put(bone, role));
        for (var entry : json.getAsJsonObject("eye_styles").entrySet()) {
            JsonObject style = entry.getValue().getAsJsonObject();
            GazeLimits limits = new GazeLimits(0.3f, 0.15f, 0.2f);
            if (style.has("gaze_limits")) {
                JsonObject values = style.getAsJsonObject("gaze_limits");
                float horizontal = limit(values, "horizontal"), up = limit(values, "up"), down = limit(values, "down");
                limits = new GazeLimits(horizontal, up, down,
                        eyeLimit(values, "left", horizontal, up, down), eyeLimit(values, "right", horizontal, up, down));
            }
            Map<String, String> bindings = new HashMap<>(CHANNELS);
            for (var binding : style.entrySet()) {
                if ("gaze_limits".equals(binding.getKey())) continue;
                if (!CHANNELS.containsKey(binding.getKey())) throw new IllegalArgumentException("未知眼型通道: " + binding.getKey());
                String bone = binding.getValue().getAsString();
                if (bone.isBlank() || bone.length() > 80) throw new IllegalArgumentException("无效眼型骨骼名");
                bindings.put(binding.getKey(), bone);
            }
            if (new HashSet<>(bindings.values()).size() != CHANNELS.size()) {
                throw new IllegalArgumentException("同一眼型的通道不能共用同一骨骼: " + entry.getKey());
            }
            bindings.forEach((role, bone) -> {
                String previousRole = boneRoles.putIfAbsent(bone, role);
                if (previousRole != null && !previousRole.equals(role)) {
                    throw new IllegalArgumentException("骨骼不能绑定到不同的表情通道: " + bone);
                }
            });
            styleBones.addAll(bindings.values());
            styles.put(entry.getKey(), new EyeStyle(entry.getKey(), Map.copyOf(bindings), limits));
        }
        if (!styles.containsKey("01") || !styles.get("01").bones.equals(CHANNELS)) {
            throw new IllegalArgumentException("01 眼型必须保留标准表情通道");
        }
        return new Catalog(Map.copyOf(definitions), Map.copyOf(animations), Map.copyOf(actions), Set.copyOf(gazeActions),
                Map.copyOf(styles), Set.copyOf(styleBones));
    }

    private static float limit(JsonObject values, String name) {
        float value = values.get(name).getAsFloat();
        if (!Float.isFinite(value) || value < 0 || value > 2.5f) throw new IllegalArgumentException("无效眼仁移动上限: " + name);
        return value;
    }

    private static EyeGaze eyeLimit(JsonObject values, String name, float horizontal, float up, float down) {
        if (!values.has(name)) return new EyeGaze(horizontal, up, down);
        JsonObject eye = values.getAsJsonObject(name);
        float outward = limit(eye, "outward"), eyeUp = limit(eye, "up"), eyeDown = limit(eye, "down");
        if (outward > horizontal || eyeUp > up || eyeDown > down) throw new IllegalArgumentException("单眼限位不能超过眼型上限: " + name);
        return new EyeGaze(outward, eyeUp, eyeDown);
    }

    private static <T> T require(Map<String, T> values, String id) {
        T value = values.get(id);
        if (value == null) throw new IllegalArgumentException("未知表情 ID: " + id);
        return value;
    }
}
