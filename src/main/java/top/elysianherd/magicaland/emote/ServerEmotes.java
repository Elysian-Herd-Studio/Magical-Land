package top.elysianherd.magicaland.emote;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import top.elysianherd.magicaland.network.NetworkHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerEmotes {
    public static final Identifier CHANNEL = new Identifier("magicaland", "emote");
    private static final long UPDATE_INTERVAL_NANOS = 100_000_000L;
    private static final long MODEL_WAIT_NANOS = 2_000_000_000L;
    private static final Map<UUID, Update> pending = new ConcurrentHashMap<>();
    private static final Map<UUID, State> states = new HashMap<>();

    private record Update(ServerPlayerEntity sender, String expression, String action, long received) {}
    private record State(String expression, String loop, long lastBroadcast) {}

    private ServerEmotes() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            try {
                String expression = buf.readString(EmoteDefinitions.MAX_ID_LENGTH);
                String action = buf.readString(EmoteDefinitions.MAX_ID_LENGTH);
                if (buf.isReadable() || !EmoteDefinitions.isExpression(expression)
                        || !EmoteDefinitions.isAction(action)) return;
                pending.put(player.getUuid(), new Update(player, expression, action, System.nanoTime()));
            } catch (RuntimeException ignored) {
            }
        });
    }

    public static void tick(MinecraftServer server) {
        long now = System.nanoTime();
        for (Map.Entry<UUID, Update> entry : pending.entrySet()) {
            UUID uuid = entry.getKey();
            Update update = entry.getValue();
            if (server.getPlayerManager().getPlayer(uuid) != update.sender()) {
                pending.remove(uuid, update);
                continue;
            }
            if (!NetworkHandler.playerModels.containsKey(uuid)) {
                if (now - update.received() >= MODEL_WAIT_NANOS) pending.remove(uuid, update);
                continue;
            }
            State previous = states.get(uuid);
            if (previous != null && now - previous.lastBroadcast() < UPDATE_INTERVAL_NANOS) continue;
            if (!pending.remove(uuid, update)) continue;
            states.put(uuid, new State(update.expression(), EmoteDefinitions.looping(update.action()) ? update.action() : "", now));
            for (ServerPlayerEntity observer : server.getPlayerManager().getPlayerList()) {
                if (!observer.getUuid().equals(uuid)) {
                    send(observer, uuid, update.expression(), update.action(), 0);
                }
            }
        }
    }

    public static void sendSnapshot(ServerPlayerEntity observer) {
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            if (!entry.getKey().equals(observer.getUuid())
                    && (!"auto".equals(entry.getValue().expression()) || !entry.getValue().loop().isEmpty())
                    && NetworkHandler.playerModels.containsKey(entry.getKey())) {
                State state = entry.getValue();
                int elapsed = EmoteDefinitions.snapshotElapsed(state.loop(),
                        (System.nanoTime() - state.lastBroadcast()) / 50_000_000L);
                send(observer, entry.getKey(), state.expression(), state.loop(), elapsed);
            }
        }
    }

    public static void remove(UUID uuid) {
        pending.remove(uuid);
        states.remove(uuid);
    }

    public static void clear() {
        pending.clear();
        states.clear();
    }

    private static void send(ServerPlayerEntity observer, UUID uuid, String expression, String action, int elapsed) {
        if (!ServerPlayNetworking.canSend(observer, CHANNEL)) return;
        try {
            var buffer = PacketByteBufs.create();
            buffer.writeUuid(uuid);
            buffer.writeString(expression, EmoteDefinitions.MAX_ID_LENGTH);
            buffer.writeString(action, EmoteDefinitions.MAX_ID_LENGTH);
            buffer.writeVarInt(elapsed);
            ServerPlayNetworking.send(observer, CHANNEL, buffer);
        } catch (RuntimeException ignored) {
        }
    }
}
