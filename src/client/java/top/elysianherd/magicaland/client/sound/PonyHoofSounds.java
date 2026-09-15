package top.elysianherd.magicaland.client.sound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.animation.PonySneakController;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.model.GeckoPlayerAnimatable;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.client.render.PonyHeldItems;

/** 声音只在客户端 tick 播放；世界动画提供相位，第一人称使用同一套步态时钟。 */
public final class PonyHoofSounds {
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final Set<BlockSoundGroup> HARD = Set.of(BlockSoundGroup.WOOD, BlockSoundGroup.STONE,
            BlockSoundGroup.METAL, BlockSoundGroup.GLASS, BlockSoundGroup.ANVIL, BlockSoundGroup.BASALT,
            BlockSoundGroup.NETHER_BRICKS, BlockSoundGroup.NETHER_ORE, BlockSoundGroup.NETHERITE,
            BlockSoundGroup.COPPER, BlockSoundGroup.TUFF, BlockSoundGroup.CALCITE, BlockSoundGroup.DRIPSTONE_BLOCK,
            BlockSoundGroup.DEEPSLATE, BlockSoundGroup.DEEPSLATE_BRICKS, BlockSoundGroup.DEEPSLATE_TILES,
            BlockSoundGroup.POLISHED_DEEPSLATE, BlockSoundGroup.BAMBOO_WOOD, BlockSoundGroup.NETHER_WOOD,
            BlockSoundGroup.CHERRY_WOOD, BlockSoundGroup.MUD_BRICKS, BlockSoundGroup.BONE);
    private static ClientWorld trackedWorld;
    private static AbstractClientPlayerEntity trackedSelf;
    private static boolean initialized;

    private PonyHoofSounds() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(PonyHoofSounds::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "hoof_sounds"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(PonyHoofSounds::clear); }
        });
    }

    public static boolean shouldReplace(AbstractClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!initialized || client.world == null || player.getWorld() != client.world
                || !Config.getInstance().replacePlayerModel || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || player.isSilent() || player.hasVehicle() || player.isSleeping()
                || player.isTouchingWater() || player.isInLava() || player.isClimbing()
                || player.isFallFlying() || player.getAbilities().flying || PonyFlightVisuals.flying(player)) return false;
        return player == client.player ? ModelManager.getAppliedModel() != null
                : ClientNetworkHandler.supportsHoofSteps() && ClientNetworkHandler.remoteModels.containsKey(player.getUuid());
    }

    /** 仅由已确定来源的原版脚步调用，不按位置猜测发声玩家。 */
    public static boolean interceptEntityStep(AbstractClientPlayerEntity player, SoundEvent sound) {
        return shouldReplace(player);
    }

    public static void observeAnimation(AbstractClientPlayerEntity player, String action, double phase, double tick) {
        if (player == null || !Double.isFinite(phase) || !Double.isFinite(tick)) return;
        Track track = TRACKS.get(player.getUuid());
        if (track == null || track.player != player || tick <= track.observedTick) return;
        track.observedAction = action;
        track.observedPhase = phase;
        track.observedTick = tick;
    }

    private static void tick(MinecraftClient client) {
        if (client.world != trackedWorld || client.player != trackedSelf) {
            clear();
            trackedWorld = client.world;
            trackedSelf = client.player;
        }
        if (client.world == null || client.player == null || !Config.getInstance().replacePlayerModel) {
            TRACKS.clear();
            return;
        }
        if (client.isPaused()) return;
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (!shouldReplace(player) || player.isSilent() || player.squaredDistanceTo(listener) > 20 * 20) continue;
            UUID id = player.getUuid();
            present.add(id);
            Track track = TRACKS.get(id);
            if (track == null || track.player != player) {
                track = new Track(player);
                TRACKS.put(id, track);
            }
            String action = track.animation.hoofAnimation();
            double limbSpeed = player.limbAnimator.getSpeed();
            Vec3d displacement = player.getPos().subtract(track.position);
            double distance = displacement.horizontalLengthSquared();
            if (distance >= 4 || displacement.lengthSquared() >= 64) {
                track.cadence.reset();
                track.observedTick = Double.NEGATIVE_INFINITY;
            }
            double elapsed = player.age - track.observedTick;
            double phase = action.equals(track.observedAction) && elapsed >= 0 && elapsed <= 3
                    ? track.observedPhase + elapsed * PonySneakController.speed(action, limbSpeed) : Double.NaN;
            boolean moving = distance > 1e-8 && distance < 4;
            track.position = player.getPos();
            // 停住、顶墙或传送都不补播积压的落蹄声。
            var impacts = track.cadence.update(action, player.age, limbSpeed, player.isOnGround(), moving, phase);
            if (client.options.getSoundVolume(SoundCategory.MASTER) <= 0
                    || client.options.getSoundVolume(SoundCategory.PLAYERS) <= 0) continue;
            for (var impact : impacts) play(client, player, impact.hoofMask(), impact.landing());
        }
        TRACKS.keySet().retainAll(present);
    }

    private static void play(MinecraftClient client, AbstractClientPlayerEntity player, int hooves, boolean landing) {
        var config = player == client.player ? ModelManager.getAppliedModel() : ClientNetworkHandler.remoteModels.get(player.getUuid());
        config = AppearanceAnatomy.apply(player.getUuid(), config);
        var held = PonyHeldItems.frame(player, config, 0);
        if (held.raises(true)) hooves &= ~PonyHoofCadence.LEFT_FRONT;
        if (held.raises(false)) hooves &= ~PonyHoofCadence.RIGHT_FRONT;
        if (hooves == 0) return;
        BlockPos pos = player.getSteppingPos();
        BlockState state = client.world.getBlockState(pos);
        BlockState above = client.world.getBlockState(pos.up());
        if (above.isIn(BlockTags.INSIDE_STEP_SOUND_BLOCKS) || above.isIn(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) state = above;
        if (state.isAir()) state = client.world.getBlockState(player.getLandingPos());
        BlockSoundGroup group = state.getSoundGroup();
        if (state.isAir() || group == BlockSoundGroup.INTENTIONALLY_EMPTY) return;
        SoundEvent sound = HARD.contains(group) ? SoundEvents.ENTITY_HORSE_STEP_WOOD : SoundEvents.ENTITY_HORSE_STEP;
        float volume = player.isSneaking() ? .028f : landing ? .18f : player.isSprinting() ? .14f : .10f;
        volume *= (float) Math.sqrt(Math.max(1, Integer.bitCount(hooves)));
        if (group == BlockSoundGroup.WOOL || group == BlockSoundGroup.MOSS_CARPET) volume *= .45f;
        volume = Math.min(.28f, volume);
        float pitch = .94f + player.getRandom().nextFloat() * .12f;
        client.getSoundManager().play(new PositionedSoundInstance(sound, SoundCategory.PLAYERS, volume, pitch,
                SoundInstance.createRandom(), player.getX(), player.getY() + .06, player.getZ()));
    }

    private static void clear() {
        TRACKS.clear();
        trackedWorld = null;
        trackedSelf = null;
    }

    private static final class Track {
        final AbstractClientPlayerEntity player;
        final GeckoPlayerAnimatable animation = new GeckoPlayerAnimatable();
        final PonyHoofCadence cadence = new PonyHoofCadence();
        Vec3d position;
        String observedAction;
        double observedTick = Double.NEGATIVE_INFINITY, observedPhase;

        Track(AbstractClientPlayerEntity player) {
            this.player = player;
            position = player.getPos();
            animation.setPlayer(player);
        }
    }
}
