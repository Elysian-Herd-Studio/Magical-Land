package top.elysianherd.magicaland.client.animation;

import java.util.UUID;

/** 世界时间窗口只提供机会，不保证每个窗口都抽耳。 */
public final class PonyIdleEars {
    static final int WINDOW_TICKS = 100;
    static final int WARMUP_TICKS = 20;
    static final int ACTIVE_TICKS = 18;
    public record Event(long window, double at, int variant) {}
    private Object owner, world;
    private double lastTime = Double.NaN, idleSince = Double.NaN;
    private Event active;

    public Event sample(Object owner, Object world, UUID id, double ticks, boolean allowed) {
        if (owner == null || world == null || id == null || !Double.isFinite(ticks) || ticks < 0) {
            reset();
            return null;
        }
        boolean changed = owner != this.owner || world != this.world || Double.isNaN(lastTime)
                || ticks < lastTime || ticks - lastTime > 5;
        double previous = lastTime;
        this.owner = owner; this.world = world; lastTime = ticks;
        if (changed || !allowed) {
            active = null;
            idleSince = allowed ? ticks : Double.NaN;
            return null;
        }
        if (Double.isNaN(idleSince)) idleSince = ticks;
        if (active != null && ticks >= active.at + ACTIVE_TICKS) active = null;
        Event candidate = candidate(id, ticks);
        if (candidate != null && previous < candidate.at && ticks >= candidate.at
                && candidate.at - idleSince >= WARMUP_TICKS) active = candidate;
        return active;
    }

    public static boolean allowed(String mainAction, boolean healthyIdle, boolean remotePermission) {
        return "idle".equals(mainAction) && healthyIdle && remotePermission;
    }

    static Event candidate(UUID id, double ticks) {
        long seed = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
        int phase = (int) Math.floorMod(seed, WINDOW_TICKS);
        long window = (long) Math.floor((ticks + phase) / WINDOW_TICKS);
        long chance = mix(seed ^ window * 0x9e3779b97f4a7c15L);
        if (Math.floorMod(chance, 100) >= 50) return null;
        int sideRoll = (int) Math.floorMod(mix(chance + 1), 100);
        int side = sideRoll < 45 ? 0 : sideRoll < 90 ? 1 : 2;
        int doubleFlick = Math.floorMod(mix(chance + 2), 100) < 20 ? 1 : 0;
        int gain = (int) Math.floorMod(mix(chance + 3), 3);
        int speed = (int) Math.floorMod(mix(chance + 4), 2);
        int variant = side * 12 + doubleFlick * 6 + gain * 2 + speed;
        double at = window * (double) WINDOW_TICKS - phase + 24 + Math.floorMod(mix(chance + 5), 48);
        return new Event(window, at, variant);
    }

    public void reset() {
        owner = world = null;
        lastTime = idleSince = Double.NaN;
        active = null;
    }

    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
