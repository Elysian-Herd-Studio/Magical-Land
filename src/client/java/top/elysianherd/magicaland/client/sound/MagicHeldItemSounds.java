package top.elysianherd.magicaland.client.sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.network.NetworkHandler;

/** 手持与悬浮共用客户端魔法音源。 */
public final class MagicHeldItemSounds {
    private static final SoundEvent CAST = SoundEvent.of(new Identifier("magicaland", "magic.cast"));
    private static final SoundEvent AURA = SoundEvent.of(new Identifier("magicaland", "magic.aura"));
    private static final SoundEvent END = SoundEvent.of(new Identifier("magicaland", "magic.end"));
    private static final MagicSoundState STATE = new MagicSoundState();
    private static final Map<UUID, Voice> LOOPS = new LinkedHashMap<>();
    private static final Map<UUID, Voice> BURSTS = new LinkedHashMap<>();
    private static ClientWorld trackedWorld;
    private static AbstractClientPlayerEntity trackedPlayer;
    private static boolean initialized;

    private MagicHeldItemSounds() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(MagicHeldItemSounds::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(MagicHeldItemSounds::clear);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "magic_held_sounds"); }
            @Override public void reload(ResourceManager manager) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> clear(client));
            }
        });
    }

    private static void tick(MinecraftClient client) {
        if (client.world != trackedWorld || client.player != trackedPlayer) {
            clear(client);
            trackedWorld = client.world;
            trackedPlayer = client.player;
        }
        if (client.world == null || client.player == null || !Config.getInstance().replacePlayerModel
                || !Config.getInstance().magicSounds
                || client.options.getSoundVolume(SoundCategory.MASTER) <= 0
                || client.options.getSoundVolume(SoundCategory.PLAYERS) <= 0) {
            clear(client);
            return;
        }
        if (client.isPaused()) return;

        Vec3d listener = client.gameRenderer.getCamera().getPos();
        List<MagicSoundState.Observation> observations = new ArrayList<>();
        Map<UUID, AbstractClientPlayerEntity> entities = new LinkedHashMap<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            boolean local = player == client.player;
            ModelConfig model = eligibleModel(client, player, local);
            if (model == null) continue;
            double distance = player.getPos().add(0, player.getStandingEyeHeight() * .75, 0).distanceTo(listener);
            if (distance >= MagicSoundState.RANGE) continue;
            UUID id = player.getUuid();
            entities.put(id, player);
            boolean holding = (!local || !client.options.getPerspective().isFirstPerson()
                    || Config.getInstance().firstPersonMagicGlow)
                    && (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty());
            boolean flying = !model.showWings && PonyFlightVisuals.flying(player);
            observations.add(new MagicSoundState.Observation(id, player.getId(), local,
                    holding, flying, distance));
        }
        MagicSoundState.Frame frame = STATE.advance(observations);
        SoundManager sounds = client.getSoundManager();
        Iterator<Map.Entry<UUID, Voice>> iterator = LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Voice> entry = iterator.next();
            Voice voice = entry.getValue();
            MagicSoundState.Observation observation = frame.eligible().get(entry.getKey());
            boolean active = frame.loops().containsKey(entry.getKey());
            // 离开范围、模型关闭等立即停止；正常放下则用包络淡出。
            if (observation == null || voice.incarnation != observation.incarnation()) {
                voice.finish(sounds);
                iterator.remove();
                continue;
            }
            voice.update(entities.get(entry.getKey()), observation.distance(), active);
            if (voice.isDone() || (voice.age >= 20 && voice.age % 20 == 0 && !sounds.isPlaying(voice))) {
                voice.finish(sounds);
                iterator.remove();
            }
        }
        for (MagicSoundState.Observation observation : frame.loops().values()) {
            if (LOOPS.containsKey(observation.id())) continue;
            if (LOOPS.size() >= MagicSoundState.MAX_LOOPS) {
                Iterator<Map.Entry<UUID, Voice>> outgoing = LOOPS.entrySet().iterator();
                while (outgoing.hasNext()) {
                    Map.Entry<UUID, Voice> entry = outgoing.next();
                    if (!frame.loops().containsKey(entry.getKey())) {
                        entry.getValue().finish(sounds);
                        outgoing.remove();
                        break;
                    }
                }
            }
            if (LOOPS.size() >= MagicSoundState.MAX_LOOPS) continue;
            Voice voice = new Voice(AURA, observation, .070f, true);
            voice.update(entities.get(observation.id()), observation.distance(), true);
            LOOPS.put(observation.id(), voice);
            sounds.play(voice);
        }
        Iterator<Voice> bursts = BURSTS.values().iterator();
        while (bursts.hasNext()) {
            Voice voice = bursts.next();
            MagicSoundState.Observation observation = frame.eligible().get(voice.playerId);
            if (observation == null || voice.incarnation != observation.incarnation() || voice.age >= 80
                    || (voice.age >= 20 && !sounds.isPlaying(voice))) {
                voice.finish(sounds);
                bursts.remove();
            } else voice.update(entities.get(voice.playerId), observation.distance(), true);
        }
        for (MagicSoundState.Event event : frame.events()) {
            MagicSoundState.Observation observation = frame.eligible().get(event.id());
            if (observation == null) continue;
            boolean cast = event.burst() == MagicSoundState.Burst.CAST;
            Voice voice = new Voice(cast ? CAST : END, observation, cast ? .24f : .18f, false);
            voice.update(entities.get(event.id()), observation.distance(), true);
            Voice previous = BURSTS.put(event.id(), voice);
            if (previous != null) previous.finish(sounds);
            sounds.play(voice);
        }
    }

    private static ModelConfig eligibleModel(MinecraftClient client, AbstractClientPlayerEntity player, boolean local) {
        if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.isInvisible()
                || player.isSleeping()) return null;
        ModelConfig model;
        if (local) {
            model = ModelManager.getAppliedModel();
        } else {
            if (!NetworkHandler.serverHasMod) return null;
            model = ClientNetworkHandler.remoteModels.get(player.getUuid());
        }
        model = AppearanceAnatomy.apply(player.getUuid(), model);
        return model != null && model.showHorn ? model : null;
    }

    private static void clear(MinecraftClient client) {
        SoundManager sounds = client.getSoundManager();
        for (Voice voice : LOOPS.values()) voice.finish(sounds);
        for (Voice voice : BURSTS.values()) voice.finish(sounds);
        LOOPS.clear();
        BURSTS.clear();
        STATE.clear();
        trackedWorld = null;
        trackedPlayer = null;
    }

    private static final class Voice extends MovingSoundInstance {
        final UUID playerId;
        final int incarnation;
        final float baseVolume;
        final MagicSoundState.Envelope envelope = new MagicSoundState.Envelope();
        int age;
        int unattendedTicks;

        Voice(SoundEvent event, MagicSoundState.Observation observation, float baseVolume, boolean looping) {
            super(event, SoundCategory.PLAYERS, SoundInstance.createRandom());
            playerId = observation.id();
            incarnation = observation.incarnation();
            this.baseVolume = baseVolume;
            repeat = looping;
            repeatDelay = 0;
            pitch = 1;
            volume = 0;
            attenuationType = AttenuationType.NONE;
        }

        void update(AbstractClientPlayerEntity player, double distance, boolean active) {
            age++;
            unattendedTicks = 0;
            x = player.getX();
            y = player.getY() + player.getStandingEyeHeight() * .75;
            z = player.getZ();
            float level = repeat ? envelope.advance(active) : 1;
            volume = baseVolume * level * MagicSoundState.distanceGain(distance);
            if (repeat && !active && level == 0) setDone();
        }

        @Override public boolean shouldAlwaysPlay() { return repeat; }
        @Override public void tick() {
            if (++unattendedTicks > 3) setDone();
        }
        void finish(SoundManager sounds) {
            setDone();
            sounds.stop(this);
        }
    }
}
