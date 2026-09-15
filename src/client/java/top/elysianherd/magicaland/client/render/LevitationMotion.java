package top.elysianherd.magicaland.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 仅驱动画面；不修改实体位置、物品使用状态或网络数据。 */
public final class LevitationMotion {
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point multiply(double n) { return new Point(x * n, y * n, z * n); }
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }

    public record Profile(double frequency, double horizontalLimit, double verticalLimit,
            double angleLimit, double trailLife, double trailLength, double trailWidth) {}
    public static final Profile WORLD = new Profile(8.5, .60, .36, 18, .40, 1.35, .055);
    public static final Profile FIRST_PERSON = new Profile(12, .20, .15, 8, .22, .55, .022);
    public static final float TRAIL_ALPHA = .28f;
    public static final int MAX_TRAIL_POINTS = 24;
    public static final Point ZERO = new Point(0, 0, 0);
    private static final double SOFT_EDGE = .72;
    private static final double TRAIL_RESPONSE = .18;
    public record Pose(Point offset, double yaw, boolean reset) {}
    public record TrailPoint(Point position, float alpha) {}
    public record Key(UUID player, boolean mainHand, boolean firstPerson) {}

    private final Profile profile;
    private final double damping, hoverPhase;
    private final boolean hovering;
    private Point goal, position, velocity = ZERO, previousPlayer;
    private double heading, angle, angularVelocity, time, started;
    private boolean initialized;
    private final List<Stamp> trail = new ArrayList<>();
    private double lastTrailTime = -Double.MAX_VALUE;
    private double trailStyleTime = Double.NaN, sprintBlend;
    private record Stamp(Point point, double time) {}

    public LevitationMotion(Profile profile) { this(profile, null); }

    public LevitationMotion(Profile profile, Key key) {
        this.profile = profile;
        damping = profile.equals(FIRST_PERSON) ? .76 : .66;
        hovering = key != null;
        long seed = key == null ? 0 : key.player.getMostSignificantBits()
                ^ Long.rotateLeft(key.player.getLeastSignificantBits(), 21)
                ^ (key.mainHand ? 0x632be59bd9b4e019L : 0x9e3779b97f4a7c15L);
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        seed ^= seed >>> 31;
        hoverPhase = (seed >>> 11) * 0x1.0p-53 * Math.PI * 2;
    }

    public Pose sample(Point target, Point player, double yaw, double seconds, boolean using) {
        if (!target.finite() || !player.finite() || !Double.isFinite(yaw) || !Double.isFinite(seconds)) {
            clear();
            return new Pose(ZERO, 0, true);
        }
        double dt = seconds - time;
        if (!initialized || dt < 0 || dt > .25 || target.subtract(goal).length() > 2
                || player.subtract(previousPlayer).length() > 2 || Math.abs(wrap(yaw - heading)) > 100) {
            goal = position = target;
            previousPlayer = player;
            velocity = ZERO;
            heading = angle = yaw;
            angularVelocity = 0;
            time = seconds;
            started = seconds;
            initialized = true;
            trail.clear();
            lastTrailTime = -Double.MAX_VALUE;
            trailStyleTime = Double.NaN;
            sprintBlend = 0;
            return new Pose(ZERO, 0, true);
        }
        if (dt > 1e-8) {
            double targetYaw = heading + wrap(yaw - heading);
            double frequency = profile.frequency * (using ? 1.65 : 1);
            double strength = using ? .22 : 1;
            Point travel = target.subtract(goal);
            Point targetVelocity = travel.multiply(1 / dt);
            double turn = targetYaw - heading, headingVelocity = turn / dt;
            int steps = Math.max(1, (int) Math.ceil(dt * 120 - 1e-9));
            double step = dt / steps;
            for (int i = 0; i < steps; i++) {
                Point from = goal.add(travel.multiply(i / (double) steps));
                Point to = goal.add(travel.multiply((i + 1.) / steps));
                double fromYaw = heading + turn * i / steps;
                double toYaw = heading + turn * (i + 1) / steps;
                double[] sx = spring(position.x, velocity.x, from.x, to.x, step, frequency, damping);
                double[] sy = spring(position.y, velocity.y, from.y, to.y, step, frequency, damping);
                double[] sz = spring(position.z, velocity.z, from.z, to.z, step, frequency, damping);
                double[] sa = spring(angle, angularVelocity, fromYaw, toYaw, step, frequency, damping);
                Constraint limited = constrain(new Point(sx[0], sy[0], sz[0]).subtract(to),
                        new Point(sx[1], sy[1], sz[1]).subtract(targetVelocity),
                        profile.horizontalLimit * strength, profile.verticalLimit * strength, step, frequency);
                position = to.add(limited.offset);
                velocity = targetVelocity.add(limited.velocity);
                double[] limitedAngle = constrainAxis(sa[0] - toYaw, sa[1] - headingVelocity,
                        profile.angleLimit * strength, step, frequency);
                angle = toYaw + limitedAngle[0];
                angularVelocity = headingVelocity + limitedAngle[1];
            }
            heading = targetYaw;
            goal = target;
            previousPlayer = player;
            time = seconds;
        }
        double strength = using ? .22 : 1;
        Point offset = bounded(position.subtract(target).add(hover(seconds, using)), profile.horizontalLimit * strength,
                profile.verticalLimit * strength);
        double yawOffset = Math.max(-profile.angleLimit * strength,
                Math.min(profile.angleLimit * strength, angle - heading));
        return new Pose(offset, yawOffset, false);
    }

    Point hover(double seconds, boolean using) {
        if (!hovering || using) return ZERO;
        double fade = Math.max(0, Math.min(1, (seconds - started) / .4));
        fade = fade * fade * (3 - 2 * fade);
        double phase = hoverPhase;
        double x = (.68 * Math.sin(seconds * .83 + phase) + .32 * Math.sin(seconds * 1.37 + phase * 1.9));
        double y = (.63 * Math.sin(seconds * 1.08 + phase * 1.3) + .37 * Math.sin(seconds * .61 + phase));
        double z = (.62 * Math.sin(seconds * .73 + phase * 1.9) + .38 * Math.sin(seconds * 1.51 + phase * 1.3));
        return new Point(x * profile.horizontalLimit * .045 * fade,
                y * profile.verticalLimit * .475 * fade, z * profile.horizontalLimit * .042 * fade);
    }

    record Constraint(Point offset, Point velocity) {}

    // 边缘逐渐回拉；速度相对于目标，不能抹掉停步后的追赶动量。
    static Constraint constrain(Point offset, Point velocity, double horizontal, double vertical, double dt, double frequency) {
        double radius = Math.hypot(offset.x, offset.z);
        double vx = velocity.x, vz = velocity.z;
        double x = offset.x, z = offset.z;
        if (radius > 1e-12) {
            double nx = x / radius, nz = z / radius;
            double outward = vx * nx + vz * nz;
            double[] radial = constrainAxis(radius, outward, horizontal, dt, frequency);
            x = nx * radial[0]; z = nz * radial[0];
            vx += nx * (radial[1] - outward);
            vz += nz * (radial[1] - outward);
        }
        double[] y = constrainAxis(offset.y, velocity.y, vertical, dt, frequency);
        return new Constraint(new Point(x, y[0], z), new Point(vx, y[1], vz));
    }

    static double[] constrainAxis(double offset, double velocity, double limit, double dt, double frequency) {
        double magnitude = Math.abs(offset), sign = Math.signum(offset);
        double softness = Math.max(0, Math.min(1, (magnitude / limit - SOFT_EDGE) / (1 - SOFT_EDGE)));
        double outward = velocity * sign;
        if (outward > 0) velocity -= sign * outward * -Math.expm1(-frequency * 4 * softness * softness * dt);
        velocity -= sign * frequency * frequency * limit * .45 * softness * softness * dt;
        if (magnitude > limit) {
            offset = sign * limit;
            if (velocity * sign > 0) velocity = 0;
        }
        return new double[] {offset, velocity};
    }

    // 对线性移动的目标解析积分，不依赖每帧固定 lerp 系数。
    private static double[] spring(double x, double v, double from, double to, double dt, double omega, double damping) {
        double targetVelocity = (to - from) / dt;
        double equilibrium = -2 * damping * targetVelocity / omega;
        double displacement = x - from - equilibrium;
        double relativeVelocity = v - targetVelocity;
        double decayRate = damping * omega;
        double oscillation = omega * Math.sqrt(1 - damping * damping);
        double sine = Math.sin(oscillation * dt), cosine = Math.cos(oscillation * dt);
        double b = (relativeVelocity + decayRate * displacement) / oscillation;
        double decay = Math.exp(-decayRate * dt);
        double relative = decay * (displacement * cosine + b * sine);
        double resultVelocity = decay * (-decayRate * (displacement * cosine + b * sine)
                + oscillation * (-displacement * sine + b * cosine));
        return new double[] {to + equilibrium + relative, targetVelocity + resultVelocity};
    }

    public List<TrailPoint> trail(Point actual, double seconds, boolean sprinting, double horizontalSpeed,
            boolean using) {
        if (!Double.isFinite(seconds)) {
            trail.clear(); lastTrailTime = -Double.MAX_VALUE; trailStyleTime = Double.NaN; sprintBlend = 0;
            return List.of();
        }
        boolean emit = actual.finite() && initialized && Double.isFinite(horizontalSpeed) && horizontalSpeed > .6 && !using;
        double elapsed = seconds - trailStyleTime;
        if (Double.isFinite(elapsed) && (elapsed < 0 || elapsed > .25)) {
            trail.clear(); lastTrailTime = -Double.MAX_VALUE; sprintBlend = 0;
        }
        if (emit && Double.isFinite(elapsed) && elapsed > 0 && elapsed <= .25) {
            double speedBlend = smooth((horizontalSpeed - 4.3) / 1.3);
            double target = sprinting ? speedBlend : 0;
            sprintBlend += (target - sprintBlend) * -Math.expm1(-elapsed / TRAIL_RESPONSE);
        }
        trailStyleTime = seconds;
        double life = trailLife(), maximumLength = trailLength();
        trail.removeIf(stamp -> seconds - stamp.time > life || seconds < stamp.time);
        // 寿命增加时适度拉开采样间隔，长尾仍保持相同的顶点预算。
        double interval = Math.max(.025, life / (MAX_TRAIL_POINTS - 2));
        if (emit && seconds - lastTrailTime + 1e-9 >= interval) {
            if (trail.isEmpty() || actual.subtract(trail.get(trail.size() - 1).point).length() >= .018) {
                trail.add(new Stamp(actual, seconds));
                lastTrailTime = seconds;
            }
        }
        while (trail.size() > MAX_TRAIL_POINTS - 1) trail.remove(0);
        if (trail.isEmpty()) return List.of();
        List<TrailPoint> result = new ArrayList<>();
        Stamp head = emit ? new Stamp(actual, seconds) : trail.get(trail.size() - 1);
        result.add(new TrailPoint(head.point, alpha(seconds, head.time)));
        double length = 0;
        Stamp previous = head;
        for (int i = trail.size() - 1; i >= 0; i--) {
            Stamp stamp = trail.get(i);
            double segment = stamp.point.subtract(previous.point).length();
            if (segment < .000001) continue;
            if (length + segment > maximumLength) {
                double remaining = maximumLength - length;
                if (remaining > .000001) {
                    double fraction = remaining / segment;
                    Point clipped = previous.point.add(stamp.point.subtract(previous.point).multiply(fraction));
                    double clippedTime = previous.time + (stamp.time - previous.time) * fraction;
                    result.add(0, new TrailPoint(clipped, alpha(seconds, clippedTime)));
                }
                break;
            }
            length += segment;
            result.add(0, new TrailPoint(stamp.point, alpha(seconds, stamp.time)));
            previous = stamp;
        }
        return List.copyOf(result);
    }

    private float alpha(double now, double emitted) {
        double life = Math.max(0, Math.min(1, 1 - (now - emitted) / trailLife()));
        return (float) (TRAIL_ALPHA * (.8 + .2 * sprintBlend) * life * life);
    }

    public float trailWidth() { return (float) (profile.trailWidth * (1 + .5 * sprintBlend)); }
    double trailLength() { return profile.trailLength * (.75 + 1.25 * sprintBlend); }
    double trailLife() { return profile.trailLife * (1 + sprintBlend); }
    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    public void clear() {
        initialized = false;
        goal = position = previousPlayer = null;
        velocity = ZERO;
        trail.clear();
        lastTrailTime = -Double.MAX_VALUE;
        trailStyleTime = Double.NaN;
        sprintBlend = 0;
    }

    static Point bounded(Point p, double horizontal, double vertical) {
        double horizontalLength = Math.hypot(p.x, p.z);
        double scale = horizontalLength > horizontal ? horizontal / horizontalLength : 1;
        return new Point(p.x * scale, Math.max(-vertical, Math.min(vertical, p.y)), p.z * scale);
    }

    static double wrap(double degrees) { return degrees - Math.floor((degrees + 180) / 360) * 360; }

    public static final class Store {
        private final Map<Key, Entry> entries = new HashMap<>();
        private Object world;
        private boolean firstPerson;
        private long frame;

        public void beginFrame(Object world, boolean firstPerson) {
            if (this.world != world || this.firstPerson != firstPerson) clear();
            this.world = world;
            this.firstPerson = firstPerson;
            frame++;
            entries.values().removeIf(entry -> frame - entry.lastSeen > 120);
        }

        public Entry acquire(Key key, String item, Profile profile) {
            Entry entry = entries.get(key);
            if (entry == null || !entry.item.equals(item)) {
                if (entries.size() >= 512) entries.clear();
                entry = new Entry(new LevitationMotion(profile, key), item);
                entries.put(key, entry);
            } else if (entry.lastSeen < frame - 1) {
                entry.motion.clear();
                entry.previousPlayer = null;
                entry.previousTime = entry.speed = 0;
                entry.updatedFrame = Long.MIN_VALUE;
                entry.pose = null;
                entry.trailFrame = Long.MIN_VALUE;
                entry.trail = List.of();
            }
            entry.lastSeen = frame;
            return entry;
        }

        public Pose sample(Entry entry, Point target, Point player, double yaw, double seconds, boolean using) {
            if (entry.updatedFrame != frame) {
                double elapsed = seconds - entry.previousTime;
                entry.speed = entry.previousPlayer != null && elapsed > 0 && elapsed <= .25
                        ? Math.hypot(player.x - entry.previousPlayer.x, player.z - entry.previousPlayer.z) / elapsed : 0;
                entry.pose = entry.motion.sample(target, player, yaw, seconds, using);
                if (entry.pose.reset()) entry.speed = 0;
                entry.previousPlayer = player;
                entry.previousTime = seconds;
                entry.updatedFrame = frame;
            }
            return entry.pose;
        }

        public List<TrailPoint> recordTrail(Entry entry, Point actual, double seconds, boolean sprinting, boolean using) {
            if (entry.trailFrame != frame) {
                entry.trail = entry.motion.trail(actual, seconds, sprinting, entry.speed, using);
                entry.trailFrame = frame;
            }
            return entry.trail;
        }

        public void remove(Key key) { entries.remove(key); }
        public void clear() { entries.clear(); }
        public int size() { return entries.size(); }
        public long frame() { return frame; }
    }

    public static final class Entry {
        final LevitationMotion motion;
        final String item;
        long lastSeen, updatedFrame = Long.MIN_VALUE;
        Pose pose;
        Point previousPlayer;
        double previousTime, speed;
        long trailFrame = Long.MIN_VALUE;
        List<TrailPoint> trail = List.of();
        Entry(LevitationMotion motion, String item) { this.motion = motion; this.item = item; }
    }
}
