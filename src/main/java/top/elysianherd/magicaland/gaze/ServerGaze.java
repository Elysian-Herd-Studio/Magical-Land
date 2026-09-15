package top.elysianherd.magicaland.gaze;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import top.elysianherd.magicaland.network.NetworkHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ServerGaze {
    private static final Map<UUID, State> states = new HashMap<>();

    private static class State {
        Identifier dimension;
        LivingEntity target;
        long lastScan = Long.MIN_VALUE;
        long lastSend = Long.MIN_VALUE;
    }

    private ServerGaze() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ServerGaze::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> states.clear());
        EntityTrackingEvents.START_TRACKING.register((tracked, observer) -> {
            if (tracked instanceof ServerPlayerEntity owner && NetworkHandler.playerModels.containsKey(owner.getUuid())) {
                State state = states.get(owner.getUuid());
                send(observer, owner, validTarget(owner, state == null ? null : state.target));
            }
        });
    }

    private static LivingEntity validTarget(ServerPlayerEntity owner, LivingEntity target) {
        return owner.isAlive() && !owner.isSpectator() && !owner.isSleeping()
                && target != null && GazeTargeting.eligible(owner, target) ? target : null;
    }

    private static void tick(MinecraftServer server) {
        var active = new HashSet<UUID>();
        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            boolean sharedModel = NetworkHandler.playerModels.containsKey(owner.getUuid());
            if (!sharedModel && !ServerPlayNetworking.canSend(owner, GazeMessage.CHANNEL)) continue;
            active.add(owner.getUuid());
            State state = states.computeIfAbsent(owner.getUuid(), ignored -> new State());
            Identifier dimension = owner.getWorld().getRegistryKey().getValue();
            boolean changedWorld = !dimension.equals(state.dimension);
            UUID previous = state.target == null ? null : state.target.getUuid();
            int previousId = state.target == null ? -1 : state.target.getId();
            state.target = changedWorld ? null : validTarget(owner, state.target);
            long now = server.getTicks();
            if (changedWorld || now - state.lastScan >= GazePolicy.SCAN_INTERVAL) {
                state.target = GazeTargeting.select(owner, state.target == null ? null : state.target.getUuid());
                state.lastScan = now;
            }
            state.dimension = dimension;
            UUID next = state.target == null ? null : state.target.getUuid();
            int nextId = state.target == null ? -1 : state.target.getId();
            if (changedWorld || !Objects.equals(previous, next) || previousId != nextId || now - state.lastSend >= 40) {
                send(owner, owner, state.target);
                if (sharedModel) {
                    for (ServerPlayerEntity observer : PlayerLookup.tracking(owner)) send(observer, owner, state.target);
                }
                state.lastSend = now;
            }
        }
        states.keySet().retainAll(active);
    }

    private static void send(ServerPlayerEntity observer, ServerPlayerEntity owner, LivingEntity target) {
        if (!ServerPlayNetworking.canSend(observer, GazeMessage.CHANNEL)) return;
        var buffer = PacketByteBufs.create();
        new GazeMessage(owner.getUuid(), owner.getWorld().getRegistryKey().getValue(),
                target == null ? -1 : target.getId(), target == null ? null : target.getUuid()).write(buffer);
        ServerPlayNetworking.send(observer, GazeMessage.CHANNEL, buffer);
    }
}
