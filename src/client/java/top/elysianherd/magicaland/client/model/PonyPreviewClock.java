package top.elysianherd.magicaland.client.model;

/** 主预览每帧采样一次；菜单暂停不停止，长时间切走不追赶补帧。 */
public final class PonyPreviewClock {
    private boolean initialized;
    private long lastNanos;
    private double ticks;

    public double sample(long nanos) {
        if (!initialized) {
            initialized = true;
            lastNanos = nanos;
        } else {
            long elapsed = nanos - lastNanos;
            if (elapsed > 0) {
                ticks += Math.min(elapsed, 250_000_000L) / 50_000_000d;
                lastNanos = nanos;
            }
        }
        return ticks;
    }

    public double ticks() { return ticks; }

    public void reset() {
        initialized = false;
        lastNanos = 0;
        ticks = 0;
    }
}
