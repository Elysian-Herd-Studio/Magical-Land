package top.elysianherd.magicaland.client.sound;

import java.util.Arrays;
import java.util.List;

/** 动画触地标记与有界的客户端 tick 调度；不访问实体、渲染或音频设备。 */
public final class PonyHoofCadence {
    public static final int LEFT_FRONT = 1, RIGHT_FRONT = 2, LEFT_HIND = 4, RIGHT_HIND = 8, ALL = 15;
    public static final double MAX_GAP_TICKS = 4, TRANSITION_TICKS = 3;
    private static final double EPSILON = 1e-7, HOOF_COOLDOWN = 2, MAX_CORRECTION = 2;
    public record Contact(int hoofMask, double seconds) {}
    public record Profile(String action, double lengthSeconds, boolean loop, List<Contact> contacts) {
        public Profile { contacts = List.copyOf(contacts); }
        public double lengthTicks() { return lengthSeconds * 20; }
    }
    public record Impact(int hoofMask, boolean landing) {}

    private static final List<Profile> PROFILES = List.of(
            new Profile("walk", .6667, true, List.of(new Contact(LEFT_HIND, 0), new Contact(LEFT_FRONT, .25),
                    new Contact(RIGHT_HIND, .2917), new Contact(RIGHT_FRONT, .625))),
            new Profile("run", .375, true, List.of(new Contact(RIGHT_HIND, 0), new Contact(RIGHT_FRONT, .0417),
                    new Contact(LEFT_FRONT | LEFT_HIND, .25))),
            new Profile("backward_walk", 1, true, List.of(new Contact(LEFT_HIND, .1944),
                    new Contact(RIGHT_FRONT, .4444), new Contact(RIGHT_HIND, .6944), new Contact(LEFT_FRONT, .9444))),
            new Profile("sneak", 1.0417, true, List.of(new Contact(RIGHT_FRONT, .25), new Contact(LEFT_HIND, .4167),
                    new Contact(LEFT_FRONT, .75), new Contact(RIGHT_HIND, .9167))),
            new Profile("land", .5, false, List.of(new Contact(ALL, 0))),
            new Profile("larger_land", 1, false, List.of(new Contact(ALL, 0))));
    private static final List<Contact> WALK_IMPACTS = List.of(new Contact(RIGHT_FRONT | LEFT_HIND, 0),
            new Contact(LEFT_FRONT | RIGHT_HIND, .2917));
    private static final List<Contact> RUN_IMPACTS = List.of(new Contact(RIGHT_FRONT | RIGHT_HIND, .0417),
            new Contact(LEFT_FRONT | LEFT_HIND, .25));

    private double previousTick = Double.NaN, airStarted = Double.NaN, phase, previousSpeed = 1;
    private Profile current;
    private boolean wasGrounded, wasMoving;
    private final double[] lastHoofTick = new double[4], lastHoofPhase = new double[4];

    public PonyHoofCadence() { reset(); }
    public static List<Profile> profiles() { return PROFILES; }
    public static Profile profile(String action) {
        for (var profile : PROFILES) if (profile.action().equals(action)) return profile;
        return null;
    }
    /** 与 PonySneakController.speed 一致；只能用连续积分，不能乘整个已播放时间。 */
    public static double speed(String action, double limbSpeed) {
        return "sneak".equals(action) && Double.isFinite(limbSpeed) ? Math.max(.25, Math.min(1, limbSpeed / .26)) : 1;
    }
    public void reset() {
        previousTick = airStarted = Double.NaN; current = null; phase = 0; previousSpeed = 1;
        wasGrounded = wasMoving = false;
        Arrays.fill(lastHoofTick, Double.NEGATIVE_INFINITY);
        Arrays.fill(lastHoofPhase, Double.NEGATIVE_INFINITY);
    }

    /** controllerPhaseTicks 是本动作的局部 tick，可循环回零；无近期世界渲染观测时传 NaN。 */
    public List<Impact> update(String action, double gameTick, double limbSpeed, boolean onGround,
                               boolean moving, double controllerPhaseTicks) {
        if (!Double.isFinite(gameTick)) { reset(); return List.of(); }
        var next = profile(action);
        double rate = speed(action, limbSpeed);
        if (!Double.isFinite(previousTick) || gameTick < previousTick) {
            reset(); baseline(next, gameTick, rate, onGround, moving, controllerPhaseTicks); return List.of();
        }
        if (gameTick == previousTick) return List.of();
        double elapsed = gameTick - previousTick;
        boolean landed = !wasGrounded && onGround && (Double.isFinite(airStarted) && gameTick - airStarted >= 3
                || "land".equals(action) || "larger_land".equals(action));
        if (onGround) airStarted = Double.NaN;
        else if (wasGrounded || !Double.isFinite(airStarted)) airStarted = gameTick;
        boolean changed = current != next || !wasMoving && moving;
        previousTick = gameTick; wasGrounded = onGround; wasMoving = moving;
        double advance = elapsed * previousSpeed; previousSpeed = rate;
        if (elapsed > MAX_GAP_TICKS) {
            airStarted = onGround ? Double.NaN : gameTick;
            baseline(next, gameTick, rate, onGround, moving, controllerPhaseTicks); return List.of();
        }
        if (changed) {
            baseline(next, gameTick, rate, onGround, moving, controllerPhaseTicks);
        }
        if (landed) {
            Arrays.fill(lastHoofTick, gameTick);
            Arrays.fill(lastHoofPhase, phase);
            return List.of(new Impact(ALL, true));
        }
        if (changed || next == null || !next.loop()) return List.of();
        double before = phase, after = phase + advance;
        if (validPhase(controllerPhaseTicks)) {
            double observed = wrap(controllerPhaseTicks, next.lengthTicks());
            observed += Math.rint((after - observed) / next.lengthTicks()) * next.lengthTicks();
            if (Math.abs(observed - after) > MAX_CORRECTION) {
                phase = observed;
                for (int i = 0; i < 4; i++) lastHoofPhase[i] = Math.max(lastHoofPhase[i], phase);
                return List.of();
            }
            // 临界帧在 loop 两侧抖动时不倒播，不重复跨过同一只蹄的标记。
            after = Math.max(before, observed);
        }
        phase = after;
        if (!moving || !onGround) {
            for (int i = 0; i < 4; i++) lastHoofPhase[i] = Math.max(lastHoofPhase[i], after);
            return List.of();
        }
        int due = 0;
        for (var contact : mergedContacts(next)) {
            double time = contact.seconds() * 20, length = next.lengthTicks();
            long first = Math.max(0, (long)Math.floor((before - time + EPSILON) / length) + 1);
            for (long cycle = first; cycle <= first + 1; cycle++) {
                double crossing = cycle * length + time;
                if (crossing > after + EPSILON) break;
                if (crossing <= 0 || crossing <= before + EPSILON) continue;
                for (int hoof = 0; hoof < 4; hoof++) if ((contact.hoofMask() & (1 << hoof)) != 0
                        && crossing > lastHoofPhase[hoof] + EPSILON) {
                    lastHoofPhase[hoof] = crossing;
                    if (gameTick - lastHoofTick[hoof] >= HOOF_COOLDOWN - EPSILON) {
                        due |= 1 << hoof; lastHoofTick[hoof] = gameTick;
                    }
                }
            }
        }
        // 同 tick 的多蹄接触只发一声；音量由调用方限幅，不按蹄数线性叠加。
        return due == 0 ? List.of() : List.of(new Impact(due, false));
    }
    private void baseline(Profile profile, double tick, double rate, boolean grounded, boolean moving, double observed) {
        current = profile; previousTick = tick; previousSpeed = rate; wasGrounded = grounded; wasMoving = moving;
        if (grounded) airStarted = Double.NaN;
        else if (!Double.isFinite(airStarted)) airStarted = tick;
        phase = profile != null && validPhase(observed) ? wrap(observed, profile.lengthTicks()) : -TRANSITION_TICKS;
        Arrays.fill(lastHoofPhase, phase);
    }
    private static boolean validPhase(double phase) { return Double.isFinite(phase) && phase >= 0 && phase < 1e9; }
    private static double wrap(double value, double length) { return value - Math.floor(value / length) * length; }
    private static List<Contact> mergedContacts(Profile profile) {
        // 相隔约一个 24 fps 作者帧的接触归到较晚帧；跨 loop 的一对也只发一次。
        return switch (profile.action()) {
            case "walk" -> WALK_IMPACTS;
            case "run" -> RUN_IMPACTS;
            default -> profile.contacts();
        };
    }
}
