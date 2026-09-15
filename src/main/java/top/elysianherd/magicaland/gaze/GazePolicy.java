package top.elysianherd.magicaland.gaze;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Predicate;

public final class GazePolicy {
    public static final double RANGE = 8;
    public static final int RECENT_ATTACK_TICKS = 100;
    public static final int SCAN_INTERVAL = 5;
    public static final double SWITCH_MARGIN = 0.2;

    public record Candidate(UUID id, int priority, double distanceSquared) {}

    private GazePolicy() {}

    public static boolean recentAttack(int age, int attackedAt) {
        long elapsed = (long) age - attackedAt;
        return elapsed >= 0 && elapsed < RECENT_ATTACK_TICKS;
    }

    public static int priority(boolean recentAttacker, boolean hostile, boolean player, boolean neutral) {
        if (recentAttacker) return 0;
        if (hostile) return 1;
        if (player) return 2;
        return neutral ? 3 : 4;
    }

    public static boolean inView(double dx, double dy, double dz, float headYaw) {
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(distanceSquared) || distanceSquared > RANGE * RANGE) return false;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 0.001) return true;
        double yaw = Math.toRadians(headYaw);
        return (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / horizontal >= Math.cos(Math.toRadians(100));
    }

    public static UUID select(Collection<Candidate> candidates, UUID current) {
        Comparator<Candidate> order = Comparator.comparingInt(Candidate::priority)
                .thenComparingDouble(Candidate::distanceSquared).thenComparing(Candidate::id);
        Candidate best = null;
        Candidate previous = null;
        for (Candidate candidate : candidates) {
            if (candidate.id == null || candidate.priority < 0 || candidate.priority > 4
                    || !Double.isFinite(candidate.distanceSquared) || candidate.distanceSquared < 0
                    || candidate.distanceSquared > RANGE * RANGE) continue;
            if (candidate.id.equals(current)) previous = candidate;
            if (best == null || order.compare(candidate, best) < 0) best = candidate;
        }
        if (best == null) return null;
        if (previous != null && previous.priority == best.priority
                && Math.sqrt(previous.distanceSquared) <= Math.sqrt(best.distanceSquared) + SWITCH_MARGIN) {
            return previous.id;
        }
        return best.id;
    }

    public static UUID selectVisible(Collection<Candidate> candidates, UUID current, Predicate<UUID> visible) {
        var remaining = new ArrayList<>(candidates);
        while (!remaining.isEmpty()) {
            UUID selected = select(remaining, current);
            if (selected == null) return null;
            if (visible.test(selected)) return selected;
            remaining.removeIf(candidate -> selected.equals(candidate.id));
        }
        return null;
    }
}
