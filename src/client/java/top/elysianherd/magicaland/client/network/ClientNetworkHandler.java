package top.elysianherd.magicaland.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.model.GeckoPlayerAnimatable;
import top.elysianherd.magicaland.client.animation.ClientGaze;
import top.elysianherd.magicaland.network.NetworkHandler;
import top.elysianherd.magicaland.network.TransformationMessage;
import top.elysianherd.magicaland.client.emote.EmoteClient;
import top.elysianherd.magicaland.client.render.TransformationParticles;
import top.elysianherd.magicaland.cutiemark.CutieMarkData;
import top.elysianherd.magicaland.sound.HoofStepProtocol;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNetworkHandler {

    private static final Gson GSON = new Gson();

    public static final Map<UUID, ModelConfig> remoteModels = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<String, String>> remoteAnimations = new ConcurrentHashMap<>();
    private static final Map<String, String> localAnimations = new ConcurrentHashMap<>();
    private static final Map<String, String> pendingAnimations = new ConcurrentHashMap<>();
    private static final GeckoPlayerAnimatable localAnimationTracker = new GeckoPlayerAnimatable();

    private static int ticksSinceJoin = -1;
    private static final int HANDSHAKE_TIMEOUT_TICKS = 60;
    private static final long MODEL_SEND_INTERVAL_NANOS = 100_000_000L;
    private static final long ANIMATION_SEND_INTERVAL_NANOS = 50_000_000L;
    private static String lastSentModelJson;
    private static String lastAppliedAppearance;
    private static String pendingModelJson;
    private static boolean pendingModelTransformation;
    private static boolean pendingModelRemoval;
    private static long lastModelSendNanos;
    private static long lastAnimationSendNanos;
    private static boolean serverHoofSteps;

    public static boolean supportsHoofSteps() { return NetworkHandler.serverHasMod && serverHoofSteps; }

    public static void register() {
        ClientGaze.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            flushPendingModelRemoval();
            flushPendingModel();
            flushPendingAnimations();

            if (ticksSinceJoin >= 0) {
                ticksSinceJoin++;
                if (ticksSinceJoin > HANDSHAKE_TIMEOUT_TICKS) {
                    ticksSinceJoin = -1;
                }
            }
            if (NetworkHandler.serverHasMod && Config.getInstance().broadcastOwnModel && client.player != null) {
                localAnimationTracker.syncLocalAnimationState(client.player);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            NetworkHandler.serverHasMod = false;
            serverHoofSteps = false;
            ticksSinceJoin = 0;
            remoteModels.clear();
            remoteAnimations.clear();
            localAnimations.clear();
            pendingAnimations.clear();
            lastSentModelJson = null;
            lastAppliedAppearance = GSON.toJson(ModelManager.getAppliedModel());
            pendingModelJson = null;
            pendingModelTransformation = false;
            pendingModelRemoval = false;
            lastModelSendNanos = 0L;
            lastAnimationSendNanos = 0L;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            NetworkHandler.serverHasMod = false;
            serverHoofSteps = false;
            ticksSinceJoin = -1;
            remoteModels.clear();
            remoteAnimations.clear();
            localAnimations.clear();
            pendingAnimations.clear();
            lastSentModelJson = null;
            lastAppliedAppearance = null;
            pendingModelJson = null;
            pendingModelTransformation = false;
            pendingModelRemoval = false;
            lastModelSendNanos = 0L;
            lastAnimationSendNanos = 0L;
            localAnimationTracker.setPlayer(null);
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    String json;
                    try {
                        json = buf.readString(NetworkHandler.MAX_MESSAGE_LENGTH);
                    } catch (RuntimeException ignored) {
                        return;
                    }

                    JsonObject msg;
                    try {
                        JsonElement parsed = JsonParser.parseString(json);
                        if (!parsed.isJsonObject()) {
                            return;
                        }
                        msg = parsed.getAsJsonObject();
                    } catch (RuntimeException ignored) {
                        return;
                    }

                    client.execute(() -> {
                        if (client.getNetworkHandler() == handler) handleMessage(msg);
                    });
                });
    }

    public static void sendModelToServer() {
        queueAppliedModel(false);
    }

    public static void publishSavedModel() {
        ModelConfig config = ModelManager.getAppliedModel();
        MinecraftClient client = MinecraftClient.getInstance();
        String appearance = config == null ? null : GSON.toJson(config);
        try {
            if (config != null && client.player != null
                    && TransformationMessage.modelChanged(lastAppliedAppearance, appearance)) {
                TransformationParticles.play(client.player, config);
            }
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger("magicaland").warn("无法播放换装光尘，模型仍正常应用", failure);
        } finally {
            lastAppliedAppearance = appearance;
            queueAppliedModel(true);
        }
    }

    private static void queueAppliedModel(boolean transformation) {
        if (!Config.getInstance().broadcastOwnModel || !NetworkHandler.serverHasMod
                || !ClientPlayNetworking.canSend(NetworkHandler.CHANNEL)) {
            return;
        }

        ModelConfig config = ModelManager.getAppliedModel();
        if (config == null)
            return;

        String modelJson = GSON.toJson(config);
        if (modelJson.length() > NetworkHandler.MAX_MODEL_DATA_LENGTH) {
            return;
        }
        if (modelJson.equals(lastSentModelJson)) {
            pendingModelJson = null;
            pendingModelTransformation = false;
            return;
        }

        long now = System.nanoTime();
        if (now - lastModelSendNanos < MODEL_SEND_INTERVAL_NANOS) {
            pendingModelTransformation = transformation
                    || (modelJson.equals(pendingModelJson) && pendingModelTransformation);
            pendingModelJson = modelJson;
            return;
        }

        transformation |= modelJson.equals(pendingModelJson) && pendingModelTransformation;
        pendingModelJson = null;
        pendingModelTransformation = false;
        sendModelPacket(modelJson, transformation);
    }

    public static void sendAnimation(String controller, String animation) {
        if (!Config.getInstance().broadcastOwnModel || !NetworkHandler.serverHasMod
                || !NetworkHandler.isAllowedAnimation(controller, animation)
                || !ClientPlayNetworking.canSend(NetworkHandler.CHANNEL)) {
            return;
        }

        if (animation.equals(localAnimations.get(controller))) {
            return;
        }

        localAnimations.put(controller, animation);
        pendingAnimations.put(controller, animation);
        flushPendingAnimations();
    }

    public static void setBroadcastOwnModel(boolean broadcast) {
        localAnimations.clear();
        pendingAnimations.clear();
        pendingModelJson = null;
        pendingModelTransformation = false;
        pendingModelRemoval = false;
        lastSentModelJson = null;

        if (!NetworkHandler.serverHasMod || !ClientPlayNetworking.canSend(NetworkHandler.CHANNEL)) {
            return;
        }

        if (broadcast) {
            sendModelToServer();
        } else {
            pendingModelRemoval = true;
            flushPendingModelRemoval();
        }
    }

    public static boolean hasRemoteAnimation(UUID uuid, String controller) {
        Map<String, String> animations = remoteAnimations.get(uuid);
        return animations != null && animations.containsKey(controller);
    }

    public static String getRemoteAnimation(UUID uuid, String controller) {
        Map<String, String> animations = remoteAnimations.get(uuid);
        return animations == null ? null : animations.get(controller);
    }

    private static void handleMessage(JsonObject msg) {
        try {
            String type = readString(msg, "type", 32);
            if (type == null) {
                return;
            }

            switch (type) {
                case "handshake" -> {
                    NetworkHandler.serverHasMod = true;
                    serverHoofSteps = HoofStepProtocol.supported(msg);
                    ClientGaze.setServerSupported(msg.has("gaze_version") && msg.get("gaze_version").getAsInt() == 1);
                    ticksSinceJoin = -1;
                    sendModelToServer();
                }
                case "model_update" -> {
                    UUID uuid = readUuid(msg, "uuid");
                    String modelData = readString(msg, "data", NetworkHandler.MAX_MODEL_DATA_LENGTH);
                    ModelConfig config = parseRemoteModel(modelData);
                    if (uuid != null && config != null) {
                        ModelConfig previous = remoteModels.put(uuid, config);
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player != null && !uuid.equals(client.player.getUuid())
                                && TransformationMessage.requested(msg)
                                && TransformationMessage.modelChanged(
                                        previous == null ? null : GSON.toJson(previous), GSON.toJson(config))) {
                            TransformationParticles.play(client.world == null ? null
                                    : client.world.getPlayerByUuid(uuid), config);
                        }
                    }
                }
                case "animation_update" -> {
                    UUID uuid = readUuid(msg, "uuid");
                    String controller = readString(msg, "controller", 32);
                    String animation = msg.has("animation")
                            ? readString(msg, "animation", NetworkHandler.MAX_ANIMATION_LENGTH)
                            : "";
                    if (uuid != null && NetworkHandler.isAllowedAnimation(controller, animation)) {
                        remoteAnimations.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                                .put(controller, animation);
                    }
                }
                case "player_remove" -> {
                    UUID uuid = readUuid(msg, "uuid");
                    if (uuid != null) {
                        remoteModels.remove(uuid);
                        remoteAnimations.remove(uuid);
                        EmoteClient.remove(uuid);
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static ModelConfig parseRemoteModel(String modelData) {
        if (modelData == null) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(modelData);
            if (!parsed.isJsonObject() || !CutieMarkData.isValidModel(parsed.getAsJsonObject())) {
                return null;
            }
            return ModelConfig.sanitize(GSON.fromJson(parsed, ModelConfig.class));
        } catch (RuntimeException ignored) {
            return null;
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

    private static UUID readUuid(JsonObject object, String name) {
        String value = readString(object, name, 36);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void flushPendingModel() {
        if (pendingModelJson == null || !Config.getInstance().broadcastOwnModel
                || !NetworkHandler.serverHasMod
                || !ClientPlayNetworking.canSend(NetworkHandler.CHANNEL)) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastModelSendNanos < MODEL_SEND_INTERVAL_NANOS) {
            return;
        }

        String modelJson = pendingModelJson;
        boolean transformation = pendingModelTransformation;
        pendingModelJson = null;
        pendingModelTransformation = false;
        if (!modelJson.equals(lastSentModelJson)) {
            sendModelPacket(modelJson, transformation);
        }
    }

    private static void flushPendingModelRemoval() {
        if (!pendingModelRemoval || !NetworkHandler.serverHasMod
                || !ClientPlayNetworking.canSend(NetworkHandler.CHANNEL)) {
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "model_remove");
        try {
            ClientPlayNetworking.send(NetworkHandler.CHANNEL,
                    PacketByteBufs.create().writeString(GSON.toJson(msg), NetworkHandler.MAX_MESSAGE_LENGTH));
            pendingModelRemoval = false;
        } catch (RuntimeException ignored) {
        }
    }

    private static void sendModelPacket(String modelJson, boolean transformation) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "model_update");
        msg.addProperty("data", modelJson);
        if (transformation) msg.addProperty("transform", true);

        try {
            ClientPlayNetworking.send(NetworkHandler.CHANNEL,
                    PacketByteBufs.create().writeString(GSON.toJson(msg), NetworkHandler.MAX_MESSAGE_LENGTH));
            lastSentModelJson = modelJson;
            lastModelSendNanos = System.nanoTime();
        } catch (RuntimeException ignored) {
            pendingModelJson = modelJson;
            pendingModelTransformation = transformation;
        }
    }

    private static void flushPendingAnimations() {
        if (pendingAnimations.isEmpty() || !Config.getInstance().broadcastOwnModel
                || !NetworkHandler.serverHasMod
                || !ClientPlayNetworking.canSend(NetworkHandler.CHANNEL)) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastAnimationSendNanos < ANIMATION_SEND_INTERVAL_NANOS) {
            return;
        }

        Map.Entry<String, String> entry = pendingAnimations.entrySet().iterator().next();
        String controller = entry.getKey();
        String animation = entry.getValue();
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "animation_update");
        msg.addProperty("controller", controller);
        msg.addProperty("animation", animation);

        try {
            ClientPlayNetworking.send(NetworkHandler.CHANNEL,
                    PacketByteBufs.create().writeString(GSON.toJson(msg), NetworkHandler.MAX_MESSAGE_LENGTH));
            pendingAnimations.remove(controller, animation);
            lastAnimationSendNanos = System.nanoTime();
        } catch (RuntimeException ignored) {
        }
    }
}
