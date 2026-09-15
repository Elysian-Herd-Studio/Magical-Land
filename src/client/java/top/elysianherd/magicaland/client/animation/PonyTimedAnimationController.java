package top.elysianherd.magicaland.client.animation;

import java.util.Map;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.state.BoneSnapshot;

/** 单段表情动作按实际经过时间播放，首次进入镜头时也保持身体与表情同步。 */
public class PonyTimedAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
    private double requestedTick = Double.NaN;
    private boolean processing, replaying, predicateCanSeek;
    private PlayState predicateResult;

    public PonyTimedAnimationController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    public void seekAnimation(double elapsedTicks) {
        if (processing && !replaying && Double.isFinite(elapsedTicks) && elapsedTicks >= 0 && elapsedTicks <= 12000)
            requestedTick = elapsedTicks;
    }

    @Override
    protected PlayState handleAnimationState(AnimationState<T> state) {
        if (replaying) return predicateResult;
        predicateResult = super.handleAnimationState(state);
        predicateCanSeek = predicateResult != PlayState.STOP && animationState != State.STOPPED;
        return predicateResult;
    }

    @Override
    public void process(CoreGeoModel<T> model, AnimationState<T> state, Map<String, CoreGeoBone> bones,
                        Map<String, BoneSnapshot> snapshots, double tick, boolean crashOnMissingBone) {
        processing = true;
        requestedTick = Double.NaN;
        try {
            super.process(model, state, bones, snapshots, tick, crashOnMissingBone);
            if (!Double.isFinite(requestedTick) || !predicateCanSeek || currentAnimation == null
                    || currentRawAnimation == null || currentRawAnimation.getAnimationStages().size() != 1
                    || requestedTick >= currentAnimation.animation().length()) return;
            double speed = getAnimationSpeed();
            if (!Double.isFinite(tick) || !Double.isFinite(speed) || speed <= 0) return;
            double offset = tick - requestedTick / speed;
            if (!Double.isFinite(offset)) return;
            if (animationState == State.RUNNING && !shouldResetTick
                    && Math.abs((tick - tickOffset) * speed - requestedTick) < .0001) return;
            // 首遍只负责初始化；重新采样会替换骨骼队列，不再执行动作选择回调。
            tickOffset = offset;
            shouldResetTick = justStartedTransition = isJustStarting = false;
            animationState = State.RUNNING;
            replaying = true;
            super.process(model, state, bones, snapshots, tick, crashOnMissingBone);
        } finally {
            processing = replaying = predicateCanSeek = false;
            requestedTick = Double.NaN;
            predicateResult = null;
        }
    }
}
