package top.elysianherd.magicaland.client.render;

/** 空手切换包络；物品互换不重播，收回沿同一路径反向行进。 */
public final class MagicEquipMotion {
    public static final double DURATION = .25;
    public static final double ENTRANCE_DELAY = .05;
    public static final double SCALE_DURATION = .10;
    public static final float MIN_VISIBLE_SCALE = .0001f;
    private boolean initialized, held;
    private double from, changed;

    public void observe(boolean present, double seconds) {
        if (!Double.isFinite(seconds)) return;
        if (!initialized || seconds < changed) {
            initialized = true;
            held = present;
            from = present ? 1 : 0;
            changed = seconds;
        } else if (held != present) {
            from = progress(seconds);
            changed = seconds;
            held = present;
        }
    }

    public float progress(double seconds) {
        if (!initialized || !Double.isFinite(seconds)) return 0;
        // 仅从完全隐藏处飞出前等待；中途倒向和收回不额外停顿。
        double delay = held && from == 0 ? ENTRANCE_DELAY : 0;
        double travel = Math.max(0, seconds - changed - delay) / DURATION;
        return (float) (held ? Math.min(1, from + travel) : Math.max(0, from - travel));
    }

    public void settle(double seconds) {
        from = held ? 1 : 0;
        changed = seconds;
    }

    public static double flight(float progress) {
        double t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }

    public static float scale(float progress) {
        return (float) flight((float) (progress * DURATION / SCALE_DURATION));
    }

    public static LevitationMotion.Point springTarget(float progress, boolean left, boolean first) {
        // 飞出是主动路径，只给弹簧少量驱动力，避免它抵消整段行程。
        return offset(progress, left, first).multiply(first ? .18 : .24);
    }

    public static final class Clock {
        private int age = Integer.MIN_VALUE;
        private double seconds = Double.NEGATIVE_INFINITY;
        public double read(int age, float delta) {
            double fraction = Float.isFinite(delta) ? Math.max(0, Math.min(1, delta)) : 0;
            double next = (age + fraction) / 20;
            seconds = age < this.age ? next : Math.max(seconds, next);
            this.age = age;
            return seconds;
        }
        public int age() { return age; }
        public double seconds() { return seconds; }
    }

    public static final class Handoff {
        private boolean active;
        public void begin() { active = true; }
        public void release() { active = false; }
        public boolean active() { return active; }
        public boolean owns(boolean transitioning, boolean stackMatches, boolean held, float equip) {
            if (transitioning) return true;
            if (stackMatches && (!held || equip <= .001f)) active = false;
            return active;
        }
    }

    public static boolean exchanged(boolean mainUnchanged, boolean offUnchanged,
            boolean mainMatchesPreviousOff, boolean offMatchesPreviousMain) {
        return (!mainUnchanged || !offUnchanged) && mainMatchesPreviousOff && offMatchesPreviousMain;
    }

    public static LevitationMotion.Point offset(float progress, boolean left, boolean first) {
        double t = flight(progress), r = 1 - t, side = left ? -1 : 1;
        // 第三人称从脑后绕外侧，第一人称从画面侧边进入，不穿过相机。
        if (first) return new LevitationMotion.Point(side * (1.5 * r + .22 * 4 * t * r),
                .28 * r + .18 * 4 * t * r, .36 * r);
        return new LevitationMotion.Point(side * (-.3 * r + .38 * 4 * t * r),
                .36 * r + .2 * 4 * t * r, 1.3 * r);
    }
}
