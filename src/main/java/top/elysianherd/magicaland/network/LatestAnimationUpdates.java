package top.elysianherd.magicaland.network;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class LatestAnimationUpdates {
    record Update(String controller, String animation) {}

    private final long intervalNanos;
    private final Set<String> controllers;
    private final Map<UUID, Long> lastApplied = new HashMap<>();
    private final Map<UUID, LinkedHashMap<String, String>> pending = new HashMap<>();

    LatestAnimationUpdates(long intervalNanos, Set<String> controllers) {
        if (intervalNanos <= 0) throw new IllegalArgumentException("Positive interval required");
        this.intervalNanos = intervalNanos;
        this.controllers = Set.copyOf(controllers);
    }

    boolean offer(UUID uuid, String controller, String animation) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(animation);
        if (!controllers.contains(controller)) return false;
        // 替换同一控制器的状态不改变排队顺序，避免其他控制器饿死。
        pending.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>()).put(controller, animation);
        return true;
    }

    Update poll(UUID uuid, long now) {
        var updates = pending.get(uuid);
        if (updates == null) return null;
        Long previous = lastApplied.get(uuid);
        if (previous != null && now - previous < intervalNanos) return null;
        var iterator = updates.entrySet().iterator();
        var entry = iterator.next();
        Update update = new Update(entry.getKey(), entry.getValue());
        iterator.remove();
        if (updates.isEmpty()) pending.remove(uuid);
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
