package top.elysianherd.magicaland.client.model;

import java.util.UUID;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import top.elysianherd.magicaland.client.animation.PonyExpressions;
import top.elysianherd.magicaland.client.animation.PonyIdleEarAnimations;
import top.elysianherd.magicaland.client.animation.PonyIdleEars;

/** 捏脸主预览专用实例，不读取或同步世界动作状态。 */
public final class PonyPreviewAnimatable extends GeckoPlayerAnimatable {
    private final PonyPreviewClock clock = new PonyPreviewClock();
    private final PonyIdleEars ears = new PonyIdleEars();
    private final UUID id = UUID.randomUUID();
    private long earWindow = Long.MIN_VALUE;

    public void beginFrame(long nanos) { clock.sample(nanos); }

    @Override public double getTick(Object object) { return clock.ticks(); }
    @Override public boolean shouldPlayAnimsWhileGamePaused() { return true; }
    @Override public boolean allowsAutomaticGaze() { return PonyExpressions.allowsAutomaticGaze("idle"); }
    @Override public boolean isPlayingEmote() { return false; }
    @Override public void syncLocalAnimationState(AbstractClientPlayerEntity player) {}

    public void prepareAnimationFrame(long instanceId, AnimationState<GeckoPlayerAnimatable> state) {
        state.setData(DataTickets.TICK, clock.ticks());
        AnimatableManager<GeckoPlayerAnimatable> manager = getAnimatableInstanceCache().getManagerForId(instanceId);
        // GeoObjectRenderer 不读 getTick；同时避开 GeoModel 首帧自动加入游戏 partialTick。
        if (manager.getFirstTickTime() == -1) manager.startedAt(0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        for (String name : new String[] {"idle", "blink_parallel", "tail_parallel"}) {
            RawAnimation animation = RawAnimation.begin().thenLoop(name);
            controllers.add(new AnimationController<>(this, name + "_preview", 0,
                    state -> state.setAndContinue(animation)));
        }
        controllers.add(new AnimationController<>(this, "ear_preview", 1, this::earPredicate));
        controllers.add(new AnimationController<>(this, "expression_controller", 0,
                state -> state.setAndContinue(PonyExpressions.forAction("idle"))));
    }

    private PlayState earPredicate(AnimationState<PonyPreviewAnimatable> state) {
        var event = ears.sample(this, this, id, clock.ticks(), true);
        if (event == null) {
            earWindow = Long.MIN_VALUE;
            state.getController().stop();
            return PlayState.STOP;
        }
        if (earWindow != event.window()) {
            earWindow = event.window();
            state.getController().forceAnimationReset();
        } else if (state.getController().hasAnimationFinished()) {
            state.getController().stop();
            return PlayState.STOP;
        }
        return state.setAndContinue(PonyIdleEarAnimations.raw(event.variant()));
    }

    public void reset() {
        clock.reset();
        ears.reset();
        earWindow = Long.MIN_VALUE;
        setPlayer(null);
    }
}
