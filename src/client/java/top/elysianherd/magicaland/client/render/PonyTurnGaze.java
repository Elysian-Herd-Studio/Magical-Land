package top.elysianherd.magicaland.client.render;

/** 无实体目标时，眼仁轻微预看转身方向；不持有目标、不改变头部。 */
final class PonyTurnGaze {
    static final float HORIZONTAL_LIMIT = .6f;
    static final float VERTICAL_LIMIT = .35f;
    static final double MAX_GAP_TICKS = 5;
    record Sample(PonyGazeMath.Offset offset, boolean resetSmoothing) {}
    private static final Sample RESET = new Sample(PonyGazeMath.Offset.ZERO, true);
    private Object owner, world;
    private double time = Double.NaN, x, y, z;
    private float yaw, pitch;
    private PonyGazeMath.Offset offset = PonyGazeMath.Offset.ZERO;

    Sample sample(Object owner, Object world, double time, float yaw, float pitch,
            double x, double y, double z, boolean entityActive) {
        if (owner == null || world == null || !Double.isFinite(time) || !Float.isFinite(yaw)
                || !Float.isFinite(pitch) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            reset();
            return RESET;
        }
        double elapsed = time - this.time;
        double yawDelta = wrap((double) yaw - this.yaw), pitchDelta = (double) pitch - this.pitch;
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        if (owner != this.owner || world != this.world || Double.isNaN(this.time)
                || elapsed < 0 || elapsed > MAX_GAP_TICKS || dx * dx + dy * dy + dz * dz > 16
                || Math.abs(yawDelta) > 100 || Math.abs(pitchDelta) > 60) {
            remember(owner, world, time, yaw, pitch, x, y, z);
            offset = PonyGazeMath.Offset.ZERO;
            return RESET;
        }
        if (entityActive) {
            remember(owner, world, time, yaw, pitch, x, y, z);
            offset = PonyGazeMath.Offset.ZERO;
            return new Sample(offset, false);
        }
        if (elapsed == 0) return new Sample(offset, false);
        remember(owner, world, time, yaw, pitch, x, y, z);
        // 正 yaw 向角色右边转，Gecko position.x 取反；正 pitch 是低头。
        offset = velocity(yawDelta / elapsed, pitchDelta / elapsed);
        return new Sample(offset, false);
    }

    static PonyGazeMath.Offset velocity(double yawPerTick, double pitchPerTick) {
        if (!Double.isFinite(yawPerTick) || !Double.isFinite(pitchPerTick)) return PonyGazeMath.Offset.ZERO;
        if (yawPerTick == 0 && pitchPerTick == 0) return PonyGazeMath.Offset.ZERO;
        return new PonyGazeMath.Offset((float) (-Math.max(-1, Math.min(1, yawPerTick / 10)) * HORIZONTAL_LIMIT),
                (float) (-Math.max(-1, Math.min(1, pitchPerTick / 7)) * VERTICAL_LIMIT));
    }

    private void remember(Object owner, Object world, double time, float yaw, float pitch, double x, double y, double z) {
        this.owner = owner; this.world = world; this.time = time;
        this.yaw = yaw; this.pitch = pitch;
        this.x = x; this.y = y; this.z = z;
    }

    void reset() {
        owner = world = null;
        time = Double.NaN;
        offset = PonyGazeMath.Offset.ZERO;
    }

    private static double wrap(double degrees) { return (degrees % 360 + 540) % 360 - 180; }
}
