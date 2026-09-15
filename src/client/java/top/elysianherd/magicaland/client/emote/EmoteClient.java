package top.elysianherd.magicaland.client.emote;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.emote.EmoteDefinitions;
import top.elysianherd.magicaland.emote.ServerEmotes;
import top.elysianherd.magicaland.network.NetworkHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EmoteClient {
    private static EmotePlayback local = new EmotePlayback();
    private static final Map<UUID, EmotePlayback> remote = new HashMap<>();
    private static ClientWorld world;
    private static AbstractClientPlayerEntity owner;
    private static long tick;
    private static boolean registered, pending, publishing;
    private static boolean restoreFirstPerson;

    private EmoteClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(EmoteClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(ServerEmotes.CHANNEL, (client, handler, buf, sender) -> {
            try {
                UUID uuid = buf.readUuid();
                String expression = buf.readString(16), action = buf.readString(16);
                int elapsedTicks = buf.isReadable() ? buf.readVarInt() : 0;
                if (buf.isReadable() || elapsedTicks < 0 || elapsedTicks > 163
                        || !EmoteDefinitions.isExpression(expression) || !EmoteDefinitions.isAction(action)) return;
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || client.player == null
                            || uuid.equals(client.player.getUuid()) || !ClientNetworkHandler.remoteModels.containsKey(uuid)) return;
                    remote.computeIfAbsent(uuid, ignored -> new EmotePlayback()).update(expression, action, tick, elapsedTicks);
                });
            } catch (RuntimeException ignored) { }
        });
    }

    public static boolean canOpen() {
        var client = MinecraftClient.getInstance();
        return client.player != null && client.world != null && client.player.isAlive()
                && !client.player.isSpectator() && Config.getInstance().replacePlayerModel
                && ModelManager.getAppliedModel() != null && !ModelManager.isEditing();
    }

    public static String selectedExpression() { return local.expression(); }
    public static String selectedAction() { return local.action(); }

    public static boolean select(String id) {
        var client = MinecraftClient.getInstance();
        if (!canOpen()) return false;
        if ("stop".equals(id)) {
            local.update("auto", "", tick);
        } else if (EmoteDefinitions.isExpression(id)) {
            local.update(id, "", tick);
        } else if (id != null && !id.isEmpty() && EmoteDefinitions.isAction(id)
                && actionAllowed(client.player)) {
            local.update("auto", id, tick);
        } else return false;
        if (local.action().isEmpty()) restorePerspective(client);
        else if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            restoreFirstPerson = true;
        }
        pending = true;
        flush();
        return true;
    }

    public static String action(AbstractClientPlayerEntity player) {
        EmotePlayback playback = state(player);
        if (playback == null) return "";
        boolean allowed = player == MinecraftClient.getInstance().player ? actionAllowed(player)
                : player.isAlive() && !player.isRemoved() && !player.isSpectator();
        return allowed ? playback.action() : "";
    }

    public static long generation(AbstractClientPlayerEntity player) {
        EmotePlayback playback = state(player);
        return playback == null ? 0 : playback.generation();
    }

    public static double elapsedTicks(AbstractClientPlayerEntity player, float partialTick) {
        EmotePlayback playback = state(player);
        return playback == null ? 0 : playback.elapsed(tick, partialTick);
    }

    public static String expression(AbstractClientPlayerEntity player) {
        EmotePlayback playback = state(player);
        return playback == null || !player.isAlive() || player.isSpectator()
                || player.hurtTime > 0 || player.isSleeping() ? "auto" : playback.expression();
    }

    public static void remove(UUID uuid) { remote.remove(uuid); }

    private static EmotePlayback state(AbstractClientPlayerEntity player) {
        var client = MinecraftClient.getInstance();
        if (player == null || client.world == null || player.getWorld() != client.world
                || !Config.getInstance().replacePlayerModel) return null;
        return player == client.player ? local : remote.get(player.getUuid());
    }

    private static boolean actionAllowed(AbstractClientPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || !player.isOnGround() || player.hurtTime > 0 || player.hasVehicle() || player.isSleeping()
                || player.isSneaking() || player.isSprinting() || player.isUsingItem() || player.handSwinging
                || player.isTouchingWater() || player.isInLava() || player.isClimbing()
                || player.getAbilities().flying || PonyFlightVisuals.flying(player)
                || player.isFallFlying() || player.isUsingRiptide()) return false;
        if (player.getVelocity().horizontalLengthSquared() > .0025) return false;
        var client = MinecraftClient.getInstance();
        if (player != client.player) return true;
        return !client.options.forwardKey.isPressed() && !client.options.backKey.isPressed()
                && !client.options.leftKey.isPressed() && !client.options.rightKey.isPressed()
                && !client.options.jumpKey.isPressed() && !client.options.sneakKey.isPressed()
                && !client.options.attackKey.isPressed() && !client.options.useKey.isPressed();
    }

    private static void tick(MinecraftClient client) {
        if (restoreFirstPerson && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            restoreFirstPerson = false;
        }
        if (client.isPaused()) return;
        tick++;
        if (world != client.world || owner != client.player) {
            restorePerspective(client);
            world = client.world;
            owner = client.player;
            if (local.stop()) pending = true;
            remote.values().forEach(playback -> {
                if (!EmoteDefinitions.looping(playback.action())) playback.stop();
            });
        }
        if (local.advance(tick, canOpen() && actionAllowed(client.player))) {
            pending = true;
            restorePerspective(client);
        }
        // 远端停止由动作发送方决定，避免较晚到达的旧飞行状态误取消新动作。
        remote.values().forEach(playback -> playback.advance(tick, true));
        boolean canPublish = Config.getInstance().broadcastOwnModel && NetworkHandler.serverHasMod
                && client.player != null && ClientPlayNetworking.canSend(ServerEmotes.CHANNEL);
        if (canPublish && !publishing) pending = true;
        publishing = canPublish;
        flush();
    }

    private static void flush() {
        if (!pending || !Config.getInstance().broadcastOwnModel || !NetworkHandler.serverHasMod
                || !ClientPlayNetworking.canSend(ServerEmotes.CHANNEL)) return;
        try {
            ClientPlayNetworking.send(ServerEmotes.CHANNEL, PacketByteBufs.create()
                    .writeString(local.expression(), 16).writeString(local.action(), 16));
            pending = false;
        } catch (RuntimeException ignored) { }
    }

    private static void clear() {
        restorePerspective(MinecraftClient.getInstance());
        local = new EmotePlayback();
        remote.clear();
        world = null;
        owner = null;
        tick = 0;
        pending = publishing = false;
    }

    private static void restorePerspective(MinecraftClient client) {
        if (restoreFirstPerson && client.options.getPerspective() == Perspective.THIRD_PERSON_BACK) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
        restoreFirstPerson = false;
    }
}
