package top.elysianherd.magicaland.client.sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 持物与魔法悬浮共享启停状态，不重复叠加音源。 */
public final class MagicSoundState {
    public static final int MAX_LOOPS = 4;
    public static final int EMPTY_DEBOUNCE_TICKS = 3;
    public static final int BURST_COOLDOWN_TICKS = 8;
    public static final double RANGE = 8;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private Set<UUID> selected = Set.of();
    private long tick;

    public record Observation(UUID id, int incarnation, boolean local, boolean holding, boolean flying, double distance) {
        public Observation(UUID id, int incarnation, boolean local, boolean holding, double distance) {
            this(id, incarnation, local, holding, false, distance);
        }
        boolean active() { return holding || flying; }
    }
    public enum Burst { CAST, END }
    public record Event(UUID id, Burst burst) {}
    public record Frame(Map<UUID, Observation> eligible, Map<UUID, Observation> loops, List<Event> events) {}

    public Frame advance(List<Observation> observations) {
        tick++;
        Map<UUID, Observation> eligible = new LinkedHashMap<>();
        List<Observation> candidates = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.id == null || !Double.isFinite(observation.distance)
                    || observation.distance < 0 || observation.distance >= RANGE
                    || eligible.putIfAbsent(observation.id, observation) != null) continue;
            PlayerState state = players.get(observation.id);
            if (state == null || state.incarnation != observation.incarnation || state.local != observation.local) {
                state = new PlayerState(observation);
                players.put(observation.id, state);
            } else {
                boolean previous = state.holding;
                if (observation.active()) {
                    state.emptyTicks = 0;
                    state.holding = true;
                } else if (state.holding && ++state.emptyTicks >= EMPTY_DEBOUNCE_TICKS) {
                    state.holding = false;
                }
                if (previous != state.holding && observation.local && tick - state.lastBurst >= BURST_COOLDOWN_TICKS) {
                    events.add(new Event(observation.id, state.holding ? Burst.CAST : Burst.END));
                    state.lastBurst = tick;
                }
            }
            if (state.holding) candidates.add(observation);
        }
        players.keySet().retainAll(eligible.keySet());
        // 小幅保留当前席位，防止两位距离相近的玩家反复抢占声道。
        candidates.sort(Comparator.comparing((Observation value) -> !value.local)
                .thenComparingDouble(value -> value.distance - (selected.contains(value.id) ? .35 : 0))
                .thenComparing(Observation::id));
        Map<UUID, Observation> loops = new LinkedHashMap<>();
        for (Observation candidate : candidates) {
            if (loops.size() == MAX_LOOPS) break;
            loops.put(candidate.id, candidate);
        }
        selected = new HashSet<>(loops.keySet());
        return new Frame(Map.copyOf(eligible), java.util.Collections.unmodifiableMap(loops), List.copyOf(events));
    }

    public void clear() {
        players.clear();
        selected = Set.of();
        tick = 0;
    }

    public int trackedPlayers() { return players.size(); }

    public static float distanceGain(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance >= RANGE) return 0;
        double fraction = Math.max(0, (distance - 2) / (RANGE - 2));
        return (float) ((1 - fraction) * (1 - fraction));
    }

    public static final class Envelope {
        private float level;
        public float advance(boolean active) {
            level = active ? Math.min(1, level + .125f) : Math.max(0, level - .1f);
            if (level < .00001f) level = 0;
            return level;
        }
        public float level() { return level; }
    }

    private static final class PlayerState {
        final int incarnation;
        final boolean local;
        boolean holding;
        int emptyTicks;
        long lastBurst = -BURST_COOLDOWN_TICKS;
        PlayerState(Observation observation) {
            incarnation = observation.incarnation;
            local = observation.local;
            holding = observation.active();
        }
    }
}
