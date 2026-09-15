package top.elysianherd.magicaland.client.model;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import top.elysianherd.magicaland.api.client.FlightPose;
import top.elysianherd.magicaland.client.animation.PonyExpressions;
import top.elysianherd.magicaland.client.animation.PonyBackwardLook;
import top.elysianherd.magicaland.client.animation.PonyIdleEars;
import top.elysianherd.magicaland.client.animation.PonyIdleEarAnimations;
import top.elysianherd.magicaland.client.animation.PonyFlightAnimations;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.animation.PonyJumpAnimation;
import top.elysianherd.magicaland.client.animation.PonyLandingAnimation;
import top.elysianherd.magicaland.client.animation.PonySneakController;
import top.elysianherd.magicaland.client.animation.PonyTimedAnimationController;
import top.elysianherd.magicaland.client.animation.PonyEmoteAnimations;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.client.emote.EmoteClient;
import top.elysianherd.magicaland.emote.EmoteDefinitions;
import top.elysianherd.magicaland.client.sound.PonyHoofSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GeckoPlayerAnimatable implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private AbstractClientPlayerEntity player;
    private String mainAnimationName;
    private String expressionAction;
    private String bodyEmote = "";
    private long bodyEmoteGeneration = Long.MIN_VALUE;
    private boolean worldSoundPass;
    private final PonyBackwardLook backwardLook = new PonyBackwardLook();
    private final PonyIdleEars idleEars = new PonyIdleEars();
    private long earEventWindow = Long.MIN_VALUE;

    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation ELYTRA_FLY_ANIM = RawAnimation.begin().thenLoop("elytra_fly");
    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation SWIM_HOLD_ANIM = RawAnimation.begin().thenLoop("swim_hold");
    private static final RawAnimation SNEAK_ANIM = RawAnimation.begin().thenLoop("sneak");
    private static final RawAnimation SNEAKING_ANIM = RawAnimation.begin().thenLoop("sneaking");
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation BACKWARD_WALK_ANIM = RawAnimation.begin().thenLoop("backward_walk");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation BLINK_ANIM = RawAnimation.begin().thenLoop("blink_parallel");
    private static final RawAnimation TAIL_ANIM = RawAnimation.begin().thenLoop("tail_parallel");
    private static final RawAnimation ATTACKED_ANIM = RawAnimation.begin().thenPlay("attacked");
    private static final RawAnimation JUMP_ANIM = RawAnimation.begin().thenPlayAndHold("jump1");
    private static final RawAnimation SLEEP_ANIM = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation BOAT_ANIM = RawAnimation.begin().thenLoop("boat");
    private static final RawAnimation RIDE_ANIM = RawAnimation.begin().thenLoop("ride");
    private static final RawAnimation RIDE_PIG_ANIM = RawAnimation.begin().thenLoop("ride_pig");
    private static final RawAnimation SIT_ANIM = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation WAVE_ANIM = RawAnimation.begin().thenPlay("wave_hand");
    private static final RawAnimation BALLET_ANIM = RawAnimation.begin().thenLoop("Ballet");
    private static final RawAnimation WAVE_FACE = RawAnimation.begin().thenPlayAndHold("face.wave");
    private static final RawAnimation BALLET_FACE = RawAnimation.begin().thenLoop("face.ballet");
    private static final RawAnimation EMOTE_OPEN_EYES = RawAnimation.begin().thenLoop(PonyEmoteAnimations.OPEN_EYES);

    private static final RawAnimation FALL_TRANSFER_ANIM = RawAnimation.begin().thenPlay("fall_transfer")
            .thenLoop("fall");
    private static final RawAnimation LAND_ONLY_ANIM = RawAnimation.begin().thenPlay("land");
    private static final RawAnimation LARGER_LAND_ONLY_ANIM = RawAnimation.begin().thenPlay("larger_land");

    private record AnimationSelection(String name, RawAnimation animation) {}

    private static class PlayerFallState {
        int fallStartTime = -1;
        final PonyJumpAnimation jump = new PonyJumpAnimation();
        final PonyLandingAnimation landing = new PonyLandingAnimation();
    }

    private final Map<UUID, PlayerFallState> fallStates = new HashMap<>();

    public GeckoPlayerAnimatable() {
    }

    public void setPlayer(AbstractClientPlayerEntity player) {
        if (this.player != player) {
            if (this.player != null)
                fallStates.remove(this.player.getUuid());
            mainAnimationName = null;
            expressionAction = null;
            bodyEmote = "";
            bodyEmoteGeneration = Long.MIN_VALUE;
            backwardLook.reset();
            idleEars.reset();
            earEventWindow = Long.MIN_VALUE;
        }
        this.player = player;
    }

    public AbstractClientPlayerEntity getPlayer() {
        return player;
    }

    public boolean worldSoundPass() { return worldSoundPass; }
    public void setWorldSoundPass(boolean enabled) { worldSoundPass = enabled; }

    public String hoofAnimation() {
        if (player == null) return "";
        if (isPlayingEmote()) return EmoteDefinitions.animation(EmoteClient.action(player));
        if (!isLocalPlayer() && ClientNetworkHandler.hasRemoteAnimation(player.getUuid(), "controller")) {
            String action = ClientNetworkHandler.getRemoteAnimation(player.getUuid(), "controller");
            return action == null ? "" : action;
        }
        AnimationSelection selection = resolveMainAnimation();
        return selection == null ? "" : selection.name();
    }

    public boolean allowsAutomaticGaze() {
        if (player == null || isPlayingEmote()) return false;
        String expression = EmoteClient.expression(player);
        if (!"auto".equals(expression)) {
            var definition = PonyExpressions.expressions().get(expression);
            return definition != null && definition.automaticGaze();
        }
        return PonyExpressions.allowsAutomaticGaze(effectiveExpressionAction());
    }

    public boolean isPlayingEmote() { return !EmoteClient.action(player).isEmpty(); }

    public PonyBackwardLook.Frame backwardLook(float partialTick) {
        return backwardLook.sample(player == null ? 0 : player.age + Math.max(0, Math.min(1, partialTick)));
    }

    private String effectiveExpressionAction() {
        String action = mainAnimationName == null ? "idle" : mainAnimationName;
        return backwardLook.expressionAction(action, player == null ? 0 : player.age);
    }

    private void setMainAnimation(String name) {
        mainAnimationName = name;
        backwardLook.update("backward_walk".equals(name), player == null ? 0 : player.age);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new PonySneakController<>(this, "controller", 3, this::predicate).observePhase((action, phase, partialTick) -> {
            if (worldSoundPass && player != null)
                PonyHoofSounds.observeAnimation(player, action, phase, player.age + (double) partialTick);
        }));
        controllers.add(new AnimationController<>(this, "blink_controller", 3, this::blinkPredicate));
        controllers.add(new PonyTimedAnimationController<>(this, "expression_controller", 3, this::expressionPredicate));
        controllers.add(new AnimationController<>(this, "ear_controller", 1, this::earPredicate));
        controllers.add(new AnimationController<>(this, "tail_controller", 3, this::tailPredicate));
    }

    private boolean isIdle() {
        if (player == null || isPlayingEmote())
            return false;

        if (player.hurtTime > 0)
            return false;

        if (player.isSleeping())
            return false;

        if (player.hasVehicle())
            return false;

        PlayerFallState fallState = fallStates.computeIfAbsent(player.getUuid(), k -> new PlayerFallState());
        boolean isOnGround = player.isOnGround();
        boolean moving = player.forwardSpeed != 0 || player.sidewaysSpeed != 0;

        if (player.getAbilities().flying || PonyFlightVisuals.flying(player))
            return false;
        if (player.isTouchingWater() && moving)
            return false;
        if (!isOnGround && !player.isTouchingWater() && !player.getAbilities().flying
                && (player.fallDistance > 0.1f || fallState.jump.isJumping(player.age, player.getVelocity().y)))
            return false;
        if (fallState.landing.isLanding())
            return false;
        if (player.isSneaking())
            return false;
        if (player.isSprinting())
            return false;
        if (player.forwardSpeed < 0)
            return false;
        if (moving)
            return false;

        return true;
    }

    private PlayState blinkPredicate(AnimationState<GeckoPlayerAnimatable> state) {
        if (player == null)
            return stopAnimation(state);
        // 停止眨眼会让 shut 恢复模型默认缩放，必须显式保持睁眼底层。
        if (isPlayingEmote()) return playAnimation(state, new AnimationSelection(PonyEmoteAnimations.OPEN_EYES, EMOTE_OPEN_EYES));

        PlayState remoteState = applyRemoteAnimation(state);
        if (remoteState != null)
            return remoteState;
        return playAnimation(state, new AnimationSelection("blink_parallel", BLINK_ANIM));
    }

    private PlayState expressionPredicate(AnimationState<GeckoPlayerAnimatable> state) {
        String emote = EmoteClient.action(player);
        String manual = EmoteClient.expression(player);
        String action = effectiveExpressionAction();
        String key = !emote.isEmpty() ? "emote:" + emote + ":" + EmoteClient.generation(player)
                : !"auto".equals(manual) ? "manual:" + manual : action;
        if (!key.equals(expressionAction)) {
            state.getController().forceAnimationReset();
            expressionAction = key;
        }
        state.getController().setAnimation(!emote.isEmpty() ? "wave".equals(emote) ? WAVE_FACE : BALLET_FACE
                : !"auto".equals(manual) ? PonyExpressions.forExpression(manual) : PonyExpressions.forAction(action));
        if (!emote.isEmpty()) seekEmote(state);
        return PlayState.CONTINUE;
    }

    private PlayState earPredicate(AnimationState<GeckoPlayerAnimatable> state) {
        boolean permission = player != null && (isLocalPlayer()
                || !ClientNetworkHandler.hasRemoteAnimation(player.getUuid(), "ear_controller")
                || "ear_parallel".equals(ClientNetworkHandler.getRemoteAnimation(player.getUuid(), "ear_controller")));
        boolean allowed = PonyIdleEars.allowed(mainAnimationName,
                player != null && player.isAlive() && !player.isSpectator() && isIdle(), permission);
        if (isLocalPlayer()) ClientNetworkHandler.sendAnimation("ear_controller", allowed ? "ear_parallel" : "");
        var event = player == null ? null : idleEars.sample(player, player.getWorld(), player.getUuid(),
                player.getWorld().getTime() + (double) state.getPartialTick(), allowed);
        if (event == null) {
            earEventWindow = Long.MIN_VALUE;
            state.getController().stop();
            return PlayState.STOP;
        }
        if (earEventWindow != event.window()) {
            earEventWindow = event.window();
            state.getController().forceAnimationReset();
        } else if (state.getController().hasAnimationFinished()) {
            state.getController().stop();
            return PlayState.STOP;
        }
        state.getController().setAnimation(PonyIdleEarAnimations.raw(event.variant()));
        return PlayState.CONTINUE;
    }

    private PlayState tailPredicate(AnimationState<GeckoPlayerAnimatable> state) {
        if (player == null || isPlayingEmote())
            return stopAnimation(state);

        PlayState remoteState = applyRemoteAnimation(state);
        if (remoteState != null)
            return remoteState;
        if (!isIdle())
            return stopAnimation(state);
        return playAnimation(state, new AnimationSelection("tail_parallel", TAIL_ANIM));
    }

    private PlayState predicate(AnimationState<GeckoPlayerAnimatable> state) {
        if (state.getController() instanceof PonySneakController<?> controller) controller.setPlaybackSpeed(1);
        if (player == null)
            return stopAnimation(state);

        AnimationSelection selection = resolveMainAnimation();
        String emote = EmoteClient.action(player);
        long generation = EmoteClient.generation(player);
        if (!emote.equals(bodyEmote) || !emote.isEmpty() && generation != bodyEmoteGeneration) {
            if ("wave".equals(bodyEmote) && !"wave".equals(emote) && state.getController() instanceof PonySneakController<?> controller)
                controller.normalizeWaveRotationsForTransition();
            if ("ballet".equals(bodyEmote) && state.getController() instanceof PonySneakController<?> controller)
                controller.normalizeRootYawForTransition();
            state.getController().forceAnimationReset();
            bodyEmote = emote;
            bodyEmoteGeneration = generation;
        }
        if (!emote.isEmpty()) {
            setMainAnimation(EmoteDefinitions.animation(emote));
            applyAnimation(state, "wave".equals(emote) ? WAVE_ANIM : BALLET_ANIM);
            seekEmote(state);
            return PlayState.CONTINUE;
        }

        PlayState remoteState = PonyFlightVisuals.wingPose(player) == null ? applyRemoteAnimation(state) : null;
        if (remoteState != null)
            return remoteState;

        return selection == null ? stopAnimation(state) : playAnimation(state, selection);
    }

    private void seekEmote(AnimationState<GeckoPlayerAnimatable> state) {
        double elapsed = EmoteClient.elapsedTicks(player, state.getPartialTick());
        if (elapsed >= 3 && state.getController() instanceof PonyTimedAnimationController<?> controller)
            controller.seekAnimation(PonyEmoteAnimations.phaseTicks(EmoteClient.action(player), elapsed));
    }

    public void syncLocalAnimationState(AbstractClientPlayerEntity localPlayer) {
        setPlayer(localPlayer);
        if (player == null || !isLocalPlayer())
            return;

        AnimationSelection main = resolveMainAnimation();
        ClientNetworkHandler.sendAnimation("controller", main == null ? "" : main.name());
        ClientNetworkHandler.sendAnimation("blink_controller", isPlayingEmote() ? "" : "blink_parallel");

        boolean idle = isIdle();
        boolean earAllowed = PonyIdleEars.allowed(main == null ? null : main.name(),
                idle && player.isAlive() && !player.isSpectator(), true);
        ClientNetworkHandler.sendAnimation("ear_controller", earAllowed ? "ear_parallel" : "");
        ClientNetworkHandler.sendAnimation("tail_controller", idle ? "tail_parallel" : "");
    }

    private AnimationSelection resolveMainAnimation() {
        if (player == null)
            return null;

        if (player.hurtTime > 0 && PonyFlightVisuals.wingPose(player) == null)
            return new AnimationSelection("attacked", ATTACKED_ANIM);

        if (player.isSleeping())
            return new AnimationSelection("sleep", SLEEP_ANIM);

        if (player.hasVehicle()) {
            net.minecraft.entity.Entity vehicle = player.getVehicle();
            if (vehicle instanceof net.minecraft.entity.passive.PigEntity) {
                return new AnimationSelection("ride_pig", RIDE_PIG_ANIM);
            }
            if (vehicle instanceof net.minecraft.entity.vehicle.BoatEntity
                    || vehicle instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity) {
                return new AnimationSelection("boat", BOAT_ANIM);
            }
            if (vehicle instanceof net.minecraft.entity.passive.AbstractHorseEntity) {
                return new AnimationSelection("ride", RIDE_ANIM);
            }
            return new AnimationSelection("sit", SIT_ANIM);
        }

        PlayerFallState fallState = fallStates.computeIfAbsent(player.getUuid(), k -> new PlayerFallState());
        boolean isOnGround = player.isOnGround();
        boolean moving = player.forwardSpeed != 0 || player.sidewaysSpeed != 0;

        boolean flying = player.getAbilities().flying || PonyFlightVisuals.flying(player);
        var landing = fallState.landing.update(player.age, player.fallDistance, isOnGround,
                flying, player.isTouchingWater(), moving || player.isSneaking());
        fallState.jump.update(player.age, player.getVelocity().y,
                !isOnGround && !flying && !player.isTouchingWater());
        if (!isOnGround && !flying && !player.isTouchingWater()) {
            if (player.fallDistance > 0.1f && fallState.fallStartTime == -1) {
                fallState.fallStartTime = player.age;
            }
        } else {
            fallState.fallStartTime = -1;
        }

        var flightConfig = PonyFlightVisuals.config(player);
        boolean authoredFlight = player.getAbilities().flying && (flightConfig == null || flightConfig.showWings);
        if (authoredFlight || PonyFlightVisuals.flying(player)) {
            var pose = PonyFlightVisuals.wingPose(player);
            boolean glide = pose == null ? player.isSprinting()
                    : pose.mode() == FlightPose.Mode.GLIDE
                    || pose.mode() == FlightPose.Mode.BOOST;
            String action = glide ? "elytra_fly" : "fly";
            return new AnimationSelection(action, flightAnimation(action));
        }

        if (player.isTouchingWater() && moving) {
            return player.isSprinting()
                    ? new AnimationSelection("swim", SWIM_ANIM)
                    : new AnimationSelection("swim_hold", SWIM_HOLD_ANIM);
        }

        if (!isOnGround && !player.isTouchingWater() && !flying) {
            if (fallState.jump.isJumping(player.age, player.getVelocity().y)) {
                return new AnimationSelection("jump1", JUMP_ANIM);
            }
            if (player.fallDistance > 0.1f && fallState.fallStartTime != -1) {
                return new AnimationSelection("fall_transfer", FALL_TRANSFER_ANIM);
            }
        }

        if (landing != PonyLandingAnimation.Landing.NONE)
            return landing == PonyLandingAnimation.Landing.HEAVY
                    ? new AnimationSelection("larger_land", LARGER_LAND_ONLY_ANIM)
                    : new AnimationSelection("land", LAND_ONLY_ANIM);

        if (player.isSneaking()) {
            return moving
                    ? new AnimationSelection("sneak", SNEAK_ANIM)
                    : new AnimationSelection("sneaking", SNEAKING_ANIM);
        }

        if (player.isSprinting())
            return new AnimationSelection("run", RUN_ANIM);
        if (player.forwardSpeed < 0)
            return new AnimationSelection("backward_walk", BACKWARD_WALK_ANIM);
        if (moving)
            return new AnimationSelection("walk", WALK_ANIM);
        return new AnimationSelection("idle", IDLE_ANIM);
    }

    private PlayState applyRemoteAnimation(AnimationState<GeckoPlayerAnimatable> state) {
        if (player == null || isLocalPlayer())
            return null;

        String controller = state.getController().getName();
        if (!ClientNetworkHandler.hasRemoteAnimation(player.getUuid(), controller))
            return null;

        String name = ClientNetworkHandler.getRemoteAnimation(player.getUuid(), controller);
        if (name == null || name.isEmpty()) {
            if ("controller".equals(controller)) setMainAnimation(null);
            state.getController().stop();
            return PlayState.STOP;
        }

        RawAnimation animation = getAnimation(controller, name);
        if (animation == null)
            return null;

        if ("controller".equals(controller)) setMainAnimation(name);

        if (isOneShot(controller, name) && state.getController().getCurrentRawAnimation() == animation
                && state.getController().hasAnimationFinished()) {
            if ("controller".equals(controller)) setMainAnimation(null);
            state.getController().stop();
            return PlayState.STOP;
        }

        applyAnimation(state, animation);
        return PlayState.CONTINUE;
    }

    private RawAnimation getAnimation(String controller, String name) {
        if ("blink_controller".equals(controller))
            return "blink_parallel".equals(name) ? BLINK_ANIM : null;
        if ("tail_controller".equals(controller))
            return "tail_parallel".equals(name) ? TAIL_ANIM : null;
        if (!"controller".equals(controller))
            return null;

        return switch (name) {
            case "fly", "elytra_fly" -> flightAnimation(name);
            case "swim" -> SWIM_ANIM;
            case "swim_hold" -> SWIM_HOLD_ANIM;
            case "sneak" -> SNEAK_ANIM;
            case "sneaking" -> SNEAKING_ANIM;
            case "run" -> RUN_ANIM;
            case "backward_walk" -> BACKWARD_WALK_ANIM;
            case "walk" -> WALK_ANIM;
            case "idle" -> IDLE_ANIM;
            case "attacked" -> ATTACKED_ANIM;
            case "jump1" -> JUMP_ANIM;
            case "sleep" -> SLEEP_ANIM;
            case "boat" -> BOAT_ANIM;
            case "ride" -> RIDE_ANIM;
            case "ride_pig" -> RIDE_PIG_ANIM;
            case "sit" -> SIT_ANIM;
            case "fall_transfer" -> FALL_TRANSFER_ANIM;
            case "land" -> LAND_ONLY_ANIM;
            case "larger_land" -> LARGER_LAND_ONLY_ANIM;
            default -> null;
        };
    }

    private boolean isOneShot(String controller, String name) {
        return "controller".equals(controller)
                && ("attacked".equals(name) || "land".equals(name) || "larger_land".equals(name));
    }

    private PlayState playAnimation(AnimationState<GeckoPlayerAnimatable> state, AnimationSelection selection) {
        if ("controller".equals(state.getController().getName())) setMainAnimation(selection.name());
        applyAnimation(state, selection.animation());
        if (isLocalPlayer()) {
            ClientNetworkHandler.sendAnimation(state.getController().getName(), selection.name());
        }
        return PlayState.CONTINUE;
    }

    private RawAnimation flightAnimation(String action) {
        var config = PonyFlightVisuals.config(player);
        return PonyFlightAnimations.select(config != null && !config.showWings && PonyFlightVisuals.eligible(player),
                "elytra_fly".equals(action) ? ELYTRA_FLY_ANIM : FLY_ANIM);
    }

    private void applyAnimation(AnimationState<GeckoPlayerAnimatable> state, RawAnimation animation) {
        var controller = state.getController();
        if (controller instanceof PonySneakController<?> locomotion) {
            locomotion.setPlaybackSpeed(PonySneakController.speed(mainAnimationName,
                    player == null ? Double.NaN : player.limbAnimator.getSpeed(state.getPartialTick())));
        }
        if ("controller".equals(controller.getName()) && controller.getCurrentRawAnimation() != animation) {
            controller.transitionLength(PonyFlightAnimations.transitionTicks(controller.getCurrentRawAnimation(), animation));
        }
        controller.setAnimation(animation);
    }

    private PlayState stopAnimation(AnimationState<GeckoPlayerAnimatable> state) {
        if ("controller".equals(state.getController().getName())) setMainAnimation(null);
        state.getController().stop();
        if (isLocalPlayer()) {
            ClientNetworkHandler.sendAnimation(state.getController().getName(), "");
        }
        return PlayState.STOP;
    }

    private boolean isLocalPlayer() {
        AbstractClientPlayerEntity localPlayer = MinecraftClient.getInstance().player;
        return localPlayer != null && player != null && localPlayer.getUuid().equals(player.getUuid());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object o) {
        return player != null ? player.age : 0;
    }
}
