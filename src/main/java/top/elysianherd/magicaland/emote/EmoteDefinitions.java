package top.elysianherd.magicaland.emote;

import java.util.Set;

public final class EmoteDefinitions {
    public static final int MAX_ID_LENGTH = 16;
    private static final Set<String> EXPRESSIONS = Set.of(
            "auto", "neutral", "happy", "angry", "closed", "scrunched");
    private static final Set<String> ACTIONS = Set.of("", "wave", "ballet");

    private EmoteDefinitions() {}

    public static boolean isExpression(String expression) {
        return expression != null && EXPRESSIONS.contains(expression);
    }

    public static boolean isAction(String action) {
        return action != null && ACTIONS.contains(action);
    }

    public static String animation(String action) {
        if ("wave".equals(action)) return "wave_hand";
        if ("ballet".equals(action)) return "Ballet";
        return null;
    }

    public static int durationTicks(String action) {
        if ("wave".equals(action)) return 38;
        if ("ballet".equals(action)) return 163;
        return 0;
    }

    public static boolean looping(String action) { return "ballet".equals(action); }

    public static int snapshotElapsed(String action, long elapsedTicks) {
        if (!looping(action)) return 0;
        long elapsed = Math.max(0, elapsedTicks);
        return (int) (elapsed < 3 ? elapsed : 3 + (elapsed - 3) % 160);
    }
}
