package top.elysianherd.magicaland.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import top.elysianherd.magicaland.gaze.ServerGaze;
import top.elysianherd.magicaland.cutiemark.CutieMarkData;
import top.elysianherd.magicaland.emote.ServerEmotes;
import top.elysianherd.magicaland.sound.HoofStepProtocol;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandler {

    public static final Identifier CHANNEL = new Identifier("magicaland", "sync");
    private static final Gson GSON = new Gson();
    public static final int MAX_MESSAGE_LENGTH = 32767;
    public static final int MAX_MODEL_DATA_LENGTH = 16384;
    public static final int MAX_ANIMATION_LENGTH = 64;

    private static final long MODEL_UPDATE_INTERVAL_NANOS = 100_000_000L;
    private static final long ANIMATION_UPDATE_INTERVAL_NANOS = 50_000_000L;
    private static final LatestModelUpdates modelUpdates = new LatestModelUpdates(MODEL_UPDATE_INTERVAL_NANOS);
    private static final Map<UUID, Long> lastTransformations = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ALLOWED_ANIMATIONS = Map.of(
            "controller", Set.of("fly", "elytra_fly", "swim", "swim_hold", "sneak", "sneaking", "run",
                    "backward_walk", "walk", "idle", "attacked", "jump1", "sleep", "boat", "ride",
                    "ride_pig", "sit", "fall_transfer", "land", "larger_land"),
            "blink_controller", Set.of("blink_parallel"),
            "ear_controller", Set.of("ear_parallel"),
            "tail_controller", Set.of("tail_parallel"));
    private static final LatestAnimationUpdates animationUpdates = new LatestAnimationUpdates(
            ANIMATION_UPDATE_INTERVAL_NANOS, ALLOWED_ANIMATIONS.keySet());

    public static final Map<UUID, String> playerModels = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<String, String>> playerAnimations = new ConcurrentHashMap<>();
    public static volatile boolean serverHasMod = false;

    public static void registerServer() {
        ServerGaze.register();
        ServerEmotes.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID uuid : modelUpdates.pendingPlayers()) applyPendingModel(server, uuid);
            for (UUID uuid : animationUpdates.pendingPlayers()) applyPendingAnimation(server, uuid);
            ServerEmotes.tick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            modelUpdates.clear();
            lastTransformations.clear();
            animationUpdates.clear();
            playerModels.clear();
            playerAnimations.clear();
            ServerEmotes.clear();
        });

        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    String json;
                    try {
                        json = buf.readString(MAX_MESSAGE_LENGTH);
                    } catch (RuntimeException ignored) {
                        return;
                    }
                    server.execute(() -> handleMessage(server, player, json));
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("type", "handshake");
            handshake.addProperty("gaze_version", 1);
            handshake.addProperty(HoofStepProtocol.CAPABILITY,
                    HoofStepProtocol.VERSION);
            send(handler.getPlayer(), GSON.toJson(handshake));
            sendAppearanceSnapshot(handler.getPlayer());
            ServerEmotes.sendSnapshot(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            playerModels.remove(uuid);
            playerAnimations.remove(uuid);
            modelUpdates.remove(uuid);
            animationUpdates.remove(uuid);
            lastTransformations.remove(uuid);
            ServerEmotes.remove(uuid);

            JsonObject remove = new JsonObject();
            remove.addProperty("type", "player_remove");
            remove.addProperty("uuid", uuid.toString());
            String removeJson = GSON.toJson(remove);

            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                send(other, removeJson);
            }
        });
    }

    public static boolean isAllowedAnimation(String controller, String animation) {
        if (controller == null || animation == null || animation.length() > MAX_ANIMATION_LENGTH) {
            return false;
        }
        Set<String> allowedAnimations = ALLOWED_ANIMATIONS.get(controller);
        return allowedAnimations != null && (animation.isEmpty() || allowedAnimations.contains(animation));
    }

    private static void handleMessage(MinecraftServer server, ServerPlayerEntity player, String json) {
        try {
            if (server.getPlayerManager().getPlayer(player.getUuid()) != player) return;
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return;
            }

            JsonObject msg = parsed.getAsJsonObject();
            String type = readString(msg, "type", 32);
            if (type == null) {
                return;
            }

            UUID uuid = player.getUuid();
            if ("model_update".equals(type)) {
                String modelData = readString(msg, "data", MAX_MODEL_DATA_LENGTH);
                if (modelData == null || !isModelData(modelData)) return;
                // 新包先替换等待项，再判断时限，不能先发出过期的旧外观。
                modelUpdates.offer(uuid, modelData, TransformationMessage.requested(msg));
                applyPendingModel(server, uuid);
            } else if ("model_remove".equals(type)) {
                boolean removedModel = playerModels.remove(uuid) != null;
                boolean removedAnimations = playerAnimations.remove(uuid) != null;
                modelUpdates.remove(uuid);
                animationUpdates.remove(uuid);
                ServerEmotes.remove(uuid);
                if (!removedModel && !removedAnimations) {
                    return;
                }

                JsonObject remove = new JsonObject();
                remove.addProperty("type", "player_remove");
                remove.addProperty("uuid", uuid.toString());
                String removeJson = GSON.toJson(remove);
                for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                    if (!other.getUuid().equals(uuid)) {
                        send(other, removeJson);
                    }
                }
            } else if ("animation_update".equals(type)) {
                String controller = readString(msg, "controller", 32);
                String animation = msg.has("animation") ? readString(msg, "animation", MAX_ANIMATION_LENGTH) : "";
                if (!playerModels.containsKey(uuid) || !isAllowedAnimation(controller, animation)) {
                    return;
                }
                animationUpdates.offer(uuid, controller, animation);
                applyPendingAnimation(server, uuid);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void applyPendingModel(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) {
            modelUpdates.remove(uuid);
            return;
        }
        long now = System.nanoTime();
        LatestModelUpdates.Update update = modelUpdates.poll(uuid, now);
        if (update == null) return;
        String modelData = update.modelData();
        String previous = playerModels.put(uuid, modelData);
        if (modelData.equals(previous)) return;

        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type", "model_update");
        broadcast.addProperty("uuid", uuid.toString());
        broadcast.addProperty("data", modelData);
        if (update.transformation()) {
            JsonObject request = new JsonObject();
            request.addProperty("transform", true);
            if (TransformationMessage.mayBroadcast(request, previous, modelData,
                    lastTransformations.get(uuid), now)) {
                lastTransformations.put(uuid, now);
                broadcast.addProperty("transform", true);
            }
        }
        String broadcastJson = GSON.toJson(broadcast);
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(uuid)) send(other, broadcastJson);
        }

    }

    private static void applyPendingAnimation(MinecraftServer server, UUID uuid) {
        if (server.getPlayerManager().getPlayer(uuid) == null || !playerModels.containsKey(uuid)) {
            animationUpdates.remove(uuid);
            return;
        }
        LatestAnimationUpdates.Update update = animationUpdates.poll(uuid, System.nanoTime());
        if (update == null) return;
        Map<String, String> animations = playerAnimations.computeIfAbsent(uuid,
                ignored -> new ConcurrentHashMap<>());
        String previous = animations.put(update.controller(), update.animation());
        if (update.animation().equals(previous)) return;
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(uuid)) {
                sendAnimationState(other, uuid, update.controller(), update.animation());
            }
        }
    }

    private static String readString(JsonObject object, String name, int maxLength) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.getAsJsonPrimitive(name).isString()) {
            return null;
        }
        String value = object.get(name).getAsString();
        return value.length() <= maxLength ? value : null;
    }

    private static boolean isModelData(String modelData) {
        try {
            JsonElement parsed = JsonParser.parseString(modelData);
            return parsed.isJsonObject() && CutieMarkData.isValidModel(parsed.getAsJsonObject());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void sendAppearanceSnapshot(ServerPlayerEntity player) {
        for (Map.Entry<UUID, String> entry : playerModels.entrySet()) {
            if (entry.getKey().equals(player.getUuid())) continue;
            JsonObject existing = new JsonObject();
            existing.addProperty("type", "model_update");
            existing.addProperty("uuid", entry.getKey().toString());
            existing.addProperty("data", entry.getValue());
            send(player, GSON.toJson(existing));
            Map<String, String> animations = playerAnimations.get(entry.getKey());
            if (animations == null) continue;
            for (Map.Entry<String, String> animation : animations.entrySet()) {
                sendAnimationState(player, entry.getKey(), animation.getKey(), animation.getValue());
            }
        }
    }

    private static void sendAnimationState(ServerPlayerEntity player, UUID uuid, String controller, String animation) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "animation_update");
        message.addProperty("uuid", uuid.toString());
        message.addProperty("controller", controller);
        message.addProperty("animation", animation);
        send(player, GSON.toJson(message));
    }

    private static void send(ServerPlayerEntity player, String json) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            return;
        }
        try {
            ServerPlayNetworking.send(player, CHANNEL,
                    PacketByteBufs.create().writeString(json, MAX_MESSAGE_LENGTH));
        } catch (RuntimeException ignored) {
        }
    }
}
