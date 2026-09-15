package top.elysianherd.magicaland.client.api;

import java.util.UUID;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.slf4j.LoggerFactory;
import top.elysianherd.magicaland.api.client.AppearanceOverrides.Visibility;
import top.elysianherd.magicaland.api.client.AnatomyOverride;
import top.elysianherd.magicaland.api.client.Registration;
import top.elysianherd.magicaland.api.client.FlightPose;

public final class AppearanceOverrideState {
    private static final OverrideRegistry<Visibility> VISIBILITY = new OverrideRegistry<>(AppearanceOverrideState::failed);
    private static final OverrideRegistry<Entity> GAZE = new OverrideRegistry<>(AppearanceOverrideState::failed);
    private static final OverrideRegistry<Boolean> MAGIC = new OverrideRegistry<>(AppearanceOverrideState::failed);
    private static final OverrideRegistry<Boolean> FLIGHT = new OverrideRegistry<>(AppearanceOverrideState::failed);
    private static final OverrideRegistry<FlightPose> FLIGHT_POSE = new OverrideRegistry<>(AppearanceOverrideState::failed);
    private static final OverrideRegistry<AnatomyOverride> ANATOMY = new OverrideRegistry<>(AppearanceOverrideState::failed);
    private static boolean initialized;

    private AppearanceOverrideState() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VISIBILITY.clear();
            GAZE.clear();
            MAGIC.clear();
            FLIGHT.clear();
            FLIGHT_POSE.clear();
            ANATOMY.clear();
        });
    }

    public static Registration registerVisibility(String ownerId, int priority, Function<UUID, Visibility> provider) {
        return VISIBILITY.register(ownerId, priority, provider);
    }

    public static Registration registerGaze(String ownerId, int priority, Function<UUID, Entity> provider) {
        return GAZE.register(ownerId, priority, provider);
    }

    public static Registration registerAnatomy(String ownerId, int priority, Function<UUID, AnatomyOverride> provider) {
        return ANATOMY.register(ownerId, priority, provider);
    }

    public static AnatomyOverride anatomy(UUID player) {
        return ANATOMY.resolve(player, value -> true, null);
    }

    public static Registration registerMagicActivity(String ownerId, int priority, Predicate<UUID> provider) {
        Objects.requireNonNull(provider, "provider");
        return MAGIC.register(ownerId, priority, provider::test);
    }

    public static Registration registerFlightActivity(String ownerId, int priority, Predicate<UUID> provider) {
        Objects.requireNonNull(provider, "provider");
        return FLIGHT.register(ownerId, priority, provider::test);
    }

    public static Registration registerFlightPose(String ownerId, int priority, Function<UUID, FlightPose> provider) {
        return FLIGHT_POSE.register(ownerId, priority, provider);
    }

    public static void unregisterOwner(String ownerId) {
        VISIBILITY.unregisterOwner(ownerId);
        GAZE.unregisterOwner(ownerId);
        MAGIC.unregisterOwner(ownerId);
        FLIGHT.unregisterOwner(ownerId);
        FLIGHT_POSE.unregisterOwner(ownerId);
        ANATOMY.unregisterOwner(ownerId);
    }

    public static Visibility visibility(UUID player) {
        return VISIBILITY.resolve(player, value -> value != Visibility.DEFAULT, Visibility.DEFAULT);
    }

    public static Entity gaze(UUID player) {
        var world = MinecraftClient.getInstance().world;
        return GAZE.resolve(player, target -> !target.isRemoved() && target.getWorld() == world, null);
    }

    public static boolean magicActive(UUID player) {
        return MAGIC.resolve(player, Boolean.TRUE::equals, false);
    }

    public static boolean flightActive(UUID player) {
        return flightPose(player) != null || FLIGHT.resolve(player, Boolean.TRUE::equals, false);
    }

    public static FlightPose flightPose(UUID player) { return FLIGHT_POSE.resolve(player, value -> true, null); }

    private static void failed(String ownerId, RuntimeException failure) {
        LoggerFactory.getLogger(AppearanceOverrideState.class).warn(
                "Removed failing appearance override from " + ownerId, failure);
    }
}
