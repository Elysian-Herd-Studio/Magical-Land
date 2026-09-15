package top.elysianherd.magicaland.client.animation;

/** 后退步态不变，头颈与眼神另按同一时间线过渡。 */
public final class PonyBackwardLook {
    public static final double DELAY_TICKS = 20;
    public static final double TURN_TICKS = 5.5;
    public enum Phase { NORMAL, WAITING, TURNING, HOLDING, RETURNING }
    public record Frame(Phase phase, float progress) {
        public boolean active() { return phase != Phase.NORMAL; }
        public boolean lookingBack() { return phase == Phase.TURNING || phase == Phase.HOLDING; }
    }
    private boolean backward;
    private boolean returning;
    private double changedAt = Double.NaN;

    public void update(boolean backward, double ticks) {
        if (!Double.isFinite(ticks)) { reset(); return; }
        if (Double.isFinite(changedAt) && ticks < changedAt) reset();
        if (this.backward == backward) return;
        Frame before = sample(ticks);
        this.backward = backward;
        returning = !backward && before.active();
        changedAt = ticks;
    }

    public Frame sample(double ticks) {
        if (!Double.isFinite(ticks) || !Double.isFinite(changedAt)) return new Frame(Phase.NORMAL, 0);
        double elapsed = Math.max(0, ticks - changedAt);
        if (backward) {
            if (elapsed < DELAY_TICKS) return new Frame(Phase.WAITING, (float) (elapsed / DELAY_TICKS));
            if (elapsed < DELAY_TICKS + TURN_TICKS)
                return new Frame(Phase.TURNING, (float) ((elapsed - DELAY_TICKS) / TURN_TICKS));
            return new Frame(Phase.HOLDING, 1);
        }
        if (returning && elapsed < TURN_TICKS) return new Frame(Phase.RETURNING, (float) (elapsed / TURN_TICKS));
        return new Frame(Phase.NORMAL, 0);
    }

    public String expressionAction(String mainAction, double ticks) {
        return "backward_walk".equals(mainAction) && !sample(ticks).lookingBack() ? "walk" : mainAction;
    }

    public void reset() {
        backward = returning = false;
        changedAt = Double.NaN;
    }
}
