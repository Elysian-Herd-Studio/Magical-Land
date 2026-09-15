package top.elysianherd.magicaland.client.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import top.elysianherd.magicaland.api.client.Registration;

final class OverrideRegistry<T> {
    private final List<Entry> entries = new ArrayList<>();
    private final BiConsumer<String, RuntimeException> onFailure;

    OverrideRegistry(BiConsumer<String, RuntimeException> onFailure) {
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
    }

    Registration register(String ownerId, int priority, Function<UUID, T> provider) {
        validateOwner(ownerId);
        Objects.requireNonNull(provider, "provider");
        Entry entry = new Entry(ownerId, priority, provider);
        entries.add(entry);
        entries.sort(Comparator.comparingInt((Entry value) -> value.priority).reversed());
        return entry;
    }

    T resolve(UUID player, Predicate<T> accepts, T fallback) {
        Objects.requireNonNull(player, "player");
        for (Entry entry : List.copyOf(entries)) {
            if (!entry.registered) continue;
            try {
                T value = entry.provider.apply(player);
                if (entry.registered && value != null && accepts.test(value)) return value;
            } catch (RuntimeException failure) {
                entry.close();
                onFailure.accept(entry.ownerId, failure);
            }
        }
        return fallback;
    }

    void unregisterOwner(String ownerId) {
        validateOwner(ownerId);
        for (Entry entry : List.copyOf(entries)) if (entry.ownerId.equals(ownerId)) entry.close();
    }

    void clear() {
        for (Entry entry : entries) {
            entry.registered = false;
            entry.provider = null;
        }
        entries.clear();
    }

    private static void validateOwner(String ownerId) {
        if (ownerId == null || !ownerId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("ownerId must be a namespaced ID such as mymod:remote_tool");
        }
    }

    private final class Entry implements Registration {
        private final String ownerId;
        private final int priority;
        private Function<UUID, T> provider;
        private boolean registered = true;

        private Entry(String ownerId, int priority, Function<UUID, T> provider) {
            this.ownerId = ownerId;
            this.priority = priority;
            this.provider = provider;
        }

        @Override public String ownerId() { return ownerId; }
        @Override public int priority() { return priority; }
        @Override public boolean isRegistered() { return registered; }
        @Override public void close() {
            if (!registered) return;
            registered = false;
            provider = null;
            entries.remove(this);
        }
    }
}
