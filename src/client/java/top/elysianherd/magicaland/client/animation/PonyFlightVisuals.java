package top.elysianherd.magicaland.client.animation;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import top.elysianherd.magicaland.api.client.AppearanceOverrides;
import top.elysianherd.magicaland.api.client.FlightPose;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PonyFlightVisuals {
    public record Frame(float amount, float magic, float bodyPitch, float bodyRoll,
                        float legPitch, float legRoll, float bob, float curlDelta, float frontLift) {
        public static final Frame NONE = new Frame(0, 0, 0, 0, 0, 0, 0, 0, 0);
        public Frame(float amount, float magic, float bodyPitch, float bodyRoll, float legPitch, float legRoll, float bob) {
            this(amount, magic, bodyPitch, bodyRoll, legPitch, legRoll, bob, 0);
        }
        public Frame(float amount, float magic, float bodyPitch, float bodyRoll, float legPitch, float legRoll, float bob, float curlDelta) {
            this(amount, magic, bodyPitch, bodyRoll, legPitch, legRoll, bob, curlDelta, 0);
        }
    }
    private record Entry(AbstractClientPlayerEntity player, PonyFlightMotion motion, PonyFlightGlow glow) {}
    private static final Map<UUID, Entry> states = new HashMap<>();
    private static ClientWorld world;
    private static boolean registered;

    private PonyFlightVisuals() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(PonyFlightVisuals::tick);
    }

    public static ModelConfig config(AbstractClientPlayerEntity player) {
        ModelConfig source = player == MinecraftClient.getInstance().player ? ModelManager.getAppliedModel()
                : ClientNetworkHandler.remoteModels.get(player.getUuid());
        return AppearanceAnatomy.apply(player.getUuid(), source);
    }

    public static boolean eligible(AbstractClientPlayerEntity player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isFallFlying() && !player.isUsingRiptide() && !player.isTouchingWater()
                && !player.isSleeping() && !player.hasVehicle();
    }

    public static boolean isFlightAction(String action) {
        return "fly".equals(action) || "elytra_fly".equals(action);
    }

    public static boolean flying(AbstractClientPlayerEntity player) {
        if (!eligible(player)) return false;
        if (wingPose(player) != null) return true;
        if (!player.isOnGround() && AppearanceOverrides.flightActive(player.getUuid())) return true;
        if (player == MinecraftClient.getInstance().player) return player.getAbilities().flying && !player.isOnGround();
        if (ClientNetworkHandler.hasRemoteAnimation(player.getUuid(), "controller"))
            return isFlightAction(ClientNetworkHandler.getRemoteAnimation(player.getUuid(), "controller"));
        return player.getAbilities().flying && !player.isOnGround();
    }

    public static FlightPose wingPose(AbstractClientPlayerEntity player) {
        if (!eligible(player)) return null;
        var config = config(player);
        return config != null && config.showWings ? AppearanceOverrides.flightPose(player.getUuid()) : null;
    }

    public static boolean sprinting(AbstractClientPlayerEntity player) {
        if (player == null) return false;
        if (player != MinecraftClient.getInstance().player
                && ClientNetworkHandler.hasRemoteAnimation(player.getUuid(), "controller"))
            return "elytra_fly".equals(ClientNetworkHandler.getRemoteAnimation(player.getUuid(), "controller"));
        return player.isSprinting();
    }

    public static Frame sample(AbstractClientPlayerEntity player, ModelConfig config, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        updateWorld(client.world);
        if (world == null || player == null || player.getWorld() != world || config == null
                || config.showWings || !eligible(player) || !Config.getInstance().replacePlayerModel) return Frame.NONE;
        Entry entry = observe(player, config);
        double phase = (player.getUuid().getLeastSignificantBits() & 65535) / 65536d * Math.PI * 2;
        var pose = entry.motion.sample(player.age + Math.max(0, Math.min(1, Float.isFinite(delta) ? delta : 0)), phase);
        return new Frame(pose.amount(), pose.magic(), pose.bodyPitch(), pose.bodyRoll(), pose.legPitch(), pose.legRoll(), pose.bob(),
                pose.curlDelta(), pose.frontLift());
    }

    private static Entry observe(AbstractClientPlayerEntity player, ModelConfig config) {
        Entry entry = states.get(player.getUuid());
        if (entry == null || entry.player != player) {
            entry = new Entry(player, new PonyFlightMotion(), new PonyFlightGlow());
            states.put(player.getUuid(), entry);
        }
        entry.motion.observe(player.age, player.getX(), player.getY(), player.getZ(), player.bodyYaw,
                flying(player), config.showHorn, sprinting(player));
        entry.glow.observe(player.age, player.getX(), player.getY(), player.getZ(), flying(player));
        return entry;
    }

    public static float auraBrightness(AbstractClientPlayerEntity player, float partialTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        updateWorld(client.world);
        if (world == null || player == null || player.getWorld() != world || !eligible(player)
                || !Config.getInstance().replacePlayerModel) return PonyFlightGlow.HOVER;
        ModelConfig config = config(player);
        if (config == null || config.showWings || !config.showHorn) return PonyFlightGlow.HOVER;
        double partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        return observe(player, config).glow.sample(player.age + partial);
    }

    private static void tick(MinecraftClient client) {
        updateWorld(client.world);
        if (world == null || !Config.getInstance().replacePlayerModel) { states.clear(); return; }
        states.entrySet().removeIf(entry -> entry.getValue().player.isRemoved()
                || world.getPlayerByUuid(entry.getKey()) != entry.getValue().player);
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            ModelConfig config = config(player);
            if (config == null || config.showWings || !eligible(player)) { states.remove(player.getUuid()); continue; }
            observe(player, config);
        }
    }

    private static void updateWorld(ClientWorld next) {
        if (world != next) { states.clear(); world = next; }
    }

    public static void clear() { states.clear(); world = null; }
}
