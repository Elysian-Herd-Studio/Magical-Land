package top.elysianherd.magicaland.client.animation;

import java.util.Map;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

/** 仅主动作使用连续的局部时钟；Gecko 的直接倍率会重新缩放已播放时间。 */
public class PonySneakController<T extends GeoAnimatable> extends PonyTimedAnimationController<T> {
    private static final double NORMAL_SNEAK_LIMB_SPEED = .26, MIN_SPEED = .25;
    private double previousTick = Double.NaN, playbackTick, playbackSpeed = 1;
    private PhaseObserver phaseObserver;
    private Map<String, CoreGeoBone> processingBones;
    private Map<String, BoneSnapshot> processingSnapshots;

    @FunctionalInterface
    public interface PhaseObserver {
        void accept(String action, double phaseTicks, float partialTick);
    }

    public PonySneakController<T> observePhase(PhaseObserver observer) {
        phaseObserver = observer;
        return this;
    }

    public PonySneakController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    public static double speed(String action, double limbSpeed) {
        if (!"sneak".equals(action) || !Double.isFinite(limbSpeed)) return 1;
        return Math.max(MIN_SPEED, Math.min(1, limbSpeed / NORMAL_SNEAK_LIMB_SPEED));
    }

    public void setPlaybackSpeed(double speed) {
        playbackSpeed = Double.isFinite(speed) ? Math.max(MIN_SPEED, Math.min(1, speed)) : 1;
    }

    public void normalizeRootYawForTransition() {
        if (processingBones == null || processingSnapshots == null) return;
        CoreGeoBone root = processingBones.get("Root");
        if (root == null) return;
        BoneSnapshot initial = root.getInitialSnapshot();
        float rest = initial == null ? 0 : initial.getRotY();
        if (!Float.isFinite(rest)) return;
        normalizeYaw(processingSnapshots.get("Root"), initial, rest);
        normalizeYaw(boneSnapshots.get("Root"), initial, rest);
        if (Float.isFinite(root.getRotY())) root.setRotY(shortestYaw(root.getRotY(), rest));
    }

    public void normalizeWaveRotationsForTransition() {
        if (processingBones == null || processingSnapshots == null) return;
        for (String name : new String[] {"LForeLeg", "RForeLeg", "LHindLeg", "RHindLeg"}) {
            if (!(processingBones.get(name) instanceof GeoBone bone)) continue;
            // 烘焙骨骼会被其他玩家复用，退场只读取本玩家的上一帧快照。
            var rotation = PonyEmotePose.localRotation(bone, processingSnapshots);
            if (!rotation.isFinite()) continue;
            PonyEmotePose.setRotation(bone, rotation);
            for (BoneSnapshot snapshot : new BoneSnapshot[] {processingSnapshots.get(name), boneSnapshots.get(name)})
                if (snapshot != null && snapshot != bone.getInitialSnapshot())
                    snapshot.updateRotation(bone.getRotX(), bone.getRotY(), bone.getRotZ());
        }
    }

    private static void normalizeYaw(BoneSnapshot snapshot, BoneSnapshot initial, float rest) {
        if (snapshot != null && snapshot != initial && Float.isFinite(snapshot.getRotY()))
            snapshot.updateRotation(snapshot.getRotX(), shortestYaw(snapshot.getRotY(), rest), snapshot.getRotZ());
    }

    private static float shortestYaw(float yaw, float rest) {
        return (float) (rest + Math.IEEEremainder((double) yaw - rest, Math.PI * 2));
    }

    @Override
    public void process(CoreGeoModel<T> model, AnimationState<T> state, Map<String, CoreGeoBone> bones,
                        Map<String, BoneSnapshot> snapshots, double tick, boolean crashOnMissingBone) {
        if (Double.isFinite(tick) && (!Double.isFinite(previousTick) || tick > previousTick)) {
            if (!Double.isFinite(previousTick)) playbackTick = tick;
            else playbackTick += (tick - previousTick) * playbackSpeed;
            previousTick = tick;
        }
        Map<String, CoreGeoBone> previousBones = processingBones;
        Map<String, BoneSnapshot> previousSnapshots = processingSnapshots;
        processingBones = bones;
        processingSnapshots = snapshots;
        try {
            // 芭蕾退出时只消去已完成的整圈，保留姿势过渡。
            super.process(model, state, bones, snapshots, playbackTick, crashOnMissingBone);
        } finally {
            processingBones = previousBones;
            processingSnapshots = previousSnapshots;
        }
        if (phaseObserver != null && animationState == State.RUNNING && currentAnimation != null) {
            phaseObserver.accept(currentAnimation.animation().name(),
                    Math.max(0, playbackTick - tickOffset) * getAnimationSpeed(), state.getPartialTick());
        }
    }
}
