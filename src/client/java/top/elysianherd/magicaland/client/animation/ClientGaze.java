package top.elysianherd.magicaland.client.animation;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.gaze.GazePolicy;
import top.elysianherd.magicaland.gaze.GazeTargeting;
import top.elysianherd.magicaland.gaze.GazeMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class ClientGaze {
    private record Snapshot(Identifier dimension, int entityId, UUID target, long receivedAt) {}
    private static final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private static final Map<UUID, LivingEntity> targets = new HashMap<>();
    private static ClientWorld world;
    private static boolean serverSupported;
    private static long ticks;

    private ClientGaze() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(ClientGaze::tick);
        ClientPlayNetworking.registerGlobalReceiver(GazeMessage.CHANNEL, (client, handler, buffer, responseSender) -> {
            try {
                GazeMessage message = GazeMessage.read(buffer);
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || client.world == null
                            || !message.dimension().equals(client.world.getRegistryKey().getValue())) return;
                    updateWorld(client.world);
                    if (snapshots.containsKey(message.owner()) || snapshots.size() < 256) {
                        snapshots.put(message.owner(), new Snapshot(message.dimension(), message.entityId(), message.target(), ticks));
                    }
                });
            } catch (RuntimeException ignored) {
            }
        });
    }

    public static void setServerSupported(boolean supported) {
        serverSupported = supported;
        targets.clear();
    }

    private static void reset() {
        world = null;
        ticks = 0;
        serverSupported = false;
        snapshots.clear();
        targets.clear();
    }

    private static void updateWorld(ClientWorld next) {
        if (next != world) {
            world = next;
            snapshots.clear();
            targets.clear();
        }
    }

    private static void tick(MinecraftClient client) {
        updateWorld(client.world);
        ticks++;
        snapshots.entrySet().removeIf(entry -> ticks - entry.getValue().receivedAt > 80);
        if (world == null || client.player == null || !Config.getInstance().automaticGaze
                || !Config.getInstance().replacePlayerModel) {
            targets.clear();
            return;
        }
        var active = new HashSet<UUID>();
        for (AbstractClientPlayerEntity viewer : world.getPlayers()) {
            boolean self = viewer == client.player;
            if (!self && !ClientNetworkHandler.remoteModels.containsKey(viewer.getUuid())) continue;
            UUID owner = viewer.getUuid();
            active.add(owner);
            LivingEntity target = null;
            if (serverSupported) {
                Snapshot snapshot = snapshots.get(owner);
                if (snapshot != null && snapshot.dimension.equals(world.getRegistryKey().getValue()) && snapshot.target != null) {
                    var entity = world.getEntityById(snapshot.entityId);
                    if (entity instanceof LivingEntity living && snapshot.target.equals(living.getUuid())) target = living;
                }
            } else {
                target = targets.get(owner);
                if (ticks % GazePolicy.SCAN_INTERVAL == 0) {
                    target = GazeTargeting.select(viewer, target == null ? null : target.getUuid());
                }
            }
            if (!viewer.isAlive() || viewer.isSpectator() || viewer.isSleeping()
                    || target == null || !GazeTargeting.eligible(viewer, target)) {
                targets.remove(owner);
            } else {
                targets.put(owner, target);
            }
        }
        targets.keySet().retainAll(active);
    }

    public static LivingEntity targetFor(AbstractClientPlayerEntity viewer) {
        if (!Config.getInstance().automaticGaze || viewer == null || viewer.getWorld() != world) return null;
        LivingEntity target = targets.get(viewer.getUuid());
        if (target == null || !target.isAlive() || target.isRemoved() || target.isInvisible()
                || target.isInvisibleTo(viewer) || target.getWorld() != world
                || viewer.squaredDistanceTo(target) > GazePolicy.RANGE * GazePolicy.RANGE) return null;
        return target;
    }
}
