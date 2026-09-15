package top.elysianherd.magicaland.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class LatestModelUpdates {
    record Update(String modelData, boolean transformation) {}

    private final long intervalNanos;
    private final Map<UUID, Long> lastApplied = new HashMap<>();
    private final Map<UUID, Update> pending = new HashMap<>();

    LatestModelUpdates(long intervalNanos) {
        if (intervalNanos <= 0) throw new IllegalArgumentException("Positive interval required");
        this.intervalNanos = intervalNanos;
    }

    void offer(UUID uuid, String modelData, boolean transformation) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(modelData);
        Update previous = pending.get(uuid);
        boolean samePendingTransformation = previous != null && previous.modelData().equals(modelData)
                && previous.transformation();
        pending.put(uuid, new Update(modelData, transformation || samePendingTransformation));
    }

    Update poll(UUID uuid, long now) {
        Update update = pending.get(uuid);
        if (update == null) return null;
        Long previous = lastApplied.get(uuid);
        if (previous != null && now - previous < intervalNanos) return null;
        pending.remove(uuid);
        lastApplied.put(uuid, now);
        return update;
    }

    List<UUID> pendingPlayers() { return List.copyOf(pending.keySet()); }

    void remove(UUID uuid) {
        pending.remove(uuid);
        lastApplied.remove(uuid);
    }

    void clear() {
        pending.clear();
        lastApplied.clear();
    }
}
