package top.elysianherd.magicaland.client.animation;

/** 亮度只按游戏刻与实际位移更新，身体与第一人称光边共用采样。 */
public final class PonyFlightGlow {
    public static final float HOVER = .55f, MOVING = .76f, ASCENDING = 1;
    private static final double RESPONSE_TICKS = 3;
    private double at = Double.NaN, x, y, z;
    private float previous = HOVER, brightness = HOVER;

    public void observe(double ticks, double x, double y, double z, boolean active) {
        if (!Double.isFinite(ticks) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            reset();
            return;
        }
        double dt = ticks - at;
        if (dt == 0) return;
        double distance = Math.sqrt(square(x - this.x) + square(y - this.y) + square(z - this.z));
        if (!Double.isFinite(at) || dt < 0 || dt > 4 || distance > 4) {
            previous = brightness = HOVER;
            at = ticks; this.x = x; this.y = y; this.z = z;
            return;
        }
        if (dt <= 0) return;
        float target = active ? target((x - this.x) / dt, (y - this.y) / dt, (z - this.z) / dt) : HOVER;
        previous = brightness;
        brightness += (target - brightness) * (float) -Math.expm1(-dt / RESPONSE_TICKS);
        at = ticks; this.x = x; this.y = y; this.z = z;
    }

    public float sample(double ticks) {
        if (!Double.isFinite(at) || !Double.isFinite(ticks) || ticks < at || ticks > at + 4) return HOVER;
        float partial = (float) Math.max(0, Math.min(1, ticks - at));
        return previous + (brightness - previous) * partial;
    }

    public static float target(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return HOVER;
        double moving = Math.max(smooth((Math.hypot(x, z) - .008) / .052), smooth((Math.abs(y) - .008) / .052));
        double rising = smooth((y - .008) / .072);
        double base = HOVER + (MOVING - HOVER) * moving;
        return (float) (base + (ASCENDING - base) * rising);
    }

    public void reset() { at = Double.NaN; previous = brightness = HOVER; }
    private static double square(double value) { return value * value; }
    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
