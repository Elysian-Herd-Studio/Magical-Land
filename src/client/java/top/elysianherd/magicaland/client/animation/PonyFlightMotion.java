package top.elysianherd.magicaland.client.animation;

/** 每游戏刻更新的纯表现状态；渲染只采样，不积累物理或改变玩家速度。 */
public final class PonyFlightMotion {
    public static final double ENTER_TICKS = 5, EXIT_TICKS = 4;
    public static final float BASE_CURL = .60f, SPRINT_CURL = .95f, MIN_CURL = .44f, MAX_CURL = 1;
    public static final float FRONT_LOWER_DEGREES = 30, FRONT_ACCEL_DEGREES = 4;
    public record Pose(float amount, float magic, float bodyPitch, float bodyRoll,
                       float legPitch, float legRoll, float bob, float curlDelta, float frontLift) {
        public static final Pose NONE = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private final Spring bodyPitch = new Spring(10), bodyRoll = new Spring(5);
    private final Spring legPitch = new Spring(18), legRoll = new Spring(8);
    private final Spring curl = new Spring(.5);
    private final Spring frontAcceleration = new Spring(FRONT_ACCEL_DEGREES), sprintBlend = new Spring(1);
    private double at = Double.NaN, x, y, z, vx, vy, vz;
    private double changedAt, fromAmount, magicAt;
    private boolean active, magic;

    public void observe(double ticks, double x, double y, double z, double yaw, boolean active, boolean magic) {
        observe(ticks, x, y, z, yaw, active, magic, false);
    }

    public void observe(double ticks, double x, double y, double z, double yaw, boolean active, boolean magic, boolean sprinting) {
        if (!finite(ticks, x, y, z, yaw)) { reset(); return; }
        double dt = ticks - at;
        if (!Double.isFinite(at) || dt < 0 || dt > 4
                || Math.sqrt(square(x - this.x) + square(y - this.y) + square(z - this.z)) > 4) {
            reset();
            at = ticks; this.x = x; this.y = y; this.z = z;
            changedAt = ticks; magicAt = ticks;
        }
        if (this.active != active) {
            fromAmount = amount(ticks);
            changedAt = ticks;
            this.active = active;
            if (active) magicAt = ticks;
        }
        this.magic = magic;
        dt = ticks - at;
        if (dt <= 0) return;
        double seconds = dt / 20;
        double nx = (x - this.x) / seconds, ny = (y - this.y) / seconds, nz = (z - this.z) / seconds;
        double sin = Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
        double forward = -nx * sin + nz * cos, side = nx * cos + nz * sin;
        double accelerationForward = (-(nx - vx) * sin + (nz - vz) * cos) / seconds;
        double accelerationSide = ((nx - vx) * cos + (nz - vz) * sin) / seconds;
        double accelerationUp = (ny - vy) / seconds;
        // 正俯仰分别表示向前倾身、向后拖腿；渲染层转换成骨骼坐标。
        bodyPitch.step(active ? forward * 1.5 : 0, seconds);
        bodyRoll.step(active ? side * .7 : 0, seconds);
        legPitch.step(active ? forward * 1.5 + clamp(accelerationForward * .12, -8, 8) : 0, seconds);
        legRoll.step(active ? side + clamp(accelerationSide * .07, -4, 4) : 0, seconds);
        // 升降只响应加速度；匀速后回到普通/冲刺收腿，不长期把腿拉直。
        double desiredCurl = (sprinting ? SPRINT_CURL : BASE_CURL) - clamp(accelerationUp * .006, -.16, .16);
        curl.step(active ? clamp(desiredCurl, MIN_CURL, MAX_CURL) - BASE_CURL : 0, seconds);
        frontAcceleration.step(active ? accelerationUp * .4 : 0, seconds);
        sprintBlend.step(active && sprinting ? 1 : 0, seconds);
        vx = nx; vy = ny; vz = nz;
        this.x = x; this.y = y; this.z = z; at = ticks;
    }

    public Pose sample(double ticks, double phase) {
        if (!Double.isFinite(at) || !finite(ticks, phase) || ticks < at || ticks > at + 4) return Pose.NONE;
        float amount = (float) amount(ticks);
        if (amount <= 0) return Pose.NONE;
        double delta = clamp(ticks - at, 0, 1);
        float glow = magic ? (float) Math.min(amount, smooth((ticks - magicAt - .75) / 4.25)) : 0;
        float bob = (float) ((Math.sin(ticks * .073 + phase) * .12
                + Math.sin(ticks * .041 + phase * .7) * .055) * amount);
        double sprint = clamp(sprintBlend.value(delta), 0, 1);
        // 蜷腿形状不变，只从根部抬升；升降加速度让整条腿短暂滞后。
        float frontLift = (float) (FRONT_LOWER_DEGREES * sprint - frontAcceleration.value(delta)) * amount;
        return new Pose(amount, glow, bodyPitch.value(delta) * amount, bodyRoll.value(delta) * amount,
                legPitch.value(delta) * amount, legRoll.value(delta) * amount, bob,
                (float) clamp(curl.value(delta), MIN_CURL - BASE_CURL, MAX_CURL - BASE_CURL) * amount,
                frontLift);
    }

    private double amount(double ticks) {
        double progress = smooth((ticks - changedAt) / (active ? ENTER_TICKS : EXIT_TICKS));
        return fromAmount + ((active ? 1 : 0) - fromAmount) * progress;
    }

    public void reset() {
        at = Double.NaN; vx = vy = vz = fromAmount = changedAt = magicAt = 0;
        active = magic = false;
        bodyPitch.reset(); bodyRoll.reset(); legPitch.reset(); legRoll.reset(); curl.reset(); frontAcceleration.reset(); sprintBlend.reset();
    }

    private static final class Spring {
        final double limit;
        double previous, value, velocity;
        Spring(double limit) { this.limit = limit; }
        void step(double target, double dt) {
            previous = value;
            target = clamp(target, -limit, limit);
            double frequency = 11, damping = .8, damped = frequency * Math.sqrt(1 - damping * damping);
            double decay = Math.exp(-damping * frequency * dt), c = Math.cos(damped * dt), s = Math.sin(damped * dt);
            double offset = value - target, b = (velocity + damping * frequency * offset) / damped;
            value = target + decay * (offset * c + b * s);
            velocity = decay * (velocity * c - (damping * frequency * b + damped * offset) * s);
            if (Math.abs(value) > limit) { value = clamp(value, -limit, limit); if (value * velocity > 0) velocity = 0; }
        }
        float value(double delta) { return (float) (previous + (value - previous) * delta); }
        void reset() { previous = value = velocity = 0; }
    }
    private static double smooth(double value) { value = clamp(value, 0, 1); return value * value * (3 - 2 * value); }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static double square(double value) { return value * value; }
    private static boolean finite(double... values) { for (double value : values) if (!Double.isFinite(value)) return false; return true; }
}
