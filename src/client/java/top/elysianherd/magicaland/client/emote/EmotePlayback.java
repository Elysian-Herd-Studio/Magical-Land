package top.elysianherd.magicaland.client.emote;

import top.elysianherd.magicaland.emote.EmoteDefinitions;

/** 挥手有固定时长，芭蕾持续循环至停止。 */
public final class EmotePlayback {
    private String expression = "auto";
    private String action = "";
    private long startedAt;
    private long generation;

    public String expression() { return expression; }
    public String action() { return action; }
    public long generation() { return generation; }
    public double elapsed(long tick, float partialTick) {
        if (action.isEmpty()) return 0;
        double fraction = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        return Math.max(0, (double) tick - startedAt + fraction);
    }

    public boolean update(String expression, String action, long tick) {
        return update(expression, action, tick, 0);
    }

    public boolean update(String expression, String action, long tick, int elapsedTicks) {
        if (!EmoteDefinitions.isExpression(expression) || !EmoteDefinitions.isAction(action)
                || elapsedTicks < 0 || elapsedTicks > 163) return false;
        this.expression = expression;
        this.action = action;
        startedAt = tick - elapsedTicks;
        generation++;
        return true;
    }

    public boolean advance(long tick, boolean allowed) {
        if (action.isEmpty()) return false;
        if (allowed && tick >= startedAt && (EmoteDefinitions.looping(action)
                || tick - startedAt < EmoteDefinitions.durationTicks(action))) return false;
        return stop();
    }

    public boolean stop() {
        if (action.isEmpty()) return false;
        action = "";
        generation++;
        return true;
    }
}
