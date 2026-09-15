package top.elysianherd.magicaland.client.animation;

import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.keyframe.KeyframeStack;
import software.bernie.geckolib.core.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.SoundKeyframeData;
import software.bernie.geckolib.core.molang.expressions.MolangValue;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 保留起飞第一帧的四肢与关节补偿，去掉振翅带动的躯干节奏。 */
public final class PonyFlightAnimations {
    public static final String NAME = "internal.levitate";
    public static final RawAnimation RAW = RawAnimation.begin().thenLoop(NAME);
    private static final Set<String> LIMBS = Set.of("LForeLeg", "LFrontCalf", "LFrontHoof",
            "RForeLeg", "RFrontCalf", "RFrontHoof", "LHindLeg", "LHindCalf", "LHindHoof",
            "RHindLeg", "RHindCalf", "RHindHoof");
    private static final Animation.Keyframes NO_EVENTS = new Animation.Keyframes(new SoundKeyframeData[0],
            new ParticleKeyframeData[0], new CustomInstructionKeyframeData[0]);
    private static Animation cachedSource, cachedPose;
    public record Limb(float rx, float ry, float rz, float x, float y, float z) {}
    private static Map<String, Limb> authoredLimbs = Map.of();

    private PonyFlightAnimations() {}

    public static RawAnimation select(boolean wingless, RawAnimation winged) { return wingless ? RAW : winged; }

    public static int transitionTicks(RawAnimation previous, RawAnimation next) {
        return next == RAW ? 5 : previous == RAW ? 4 : 3;
    }

    public static synchronized Animation resolve(Animation source) {
        if (source != cachedSource || cachedPose == null) {
            List<BoneAnimation> bones = new ArrayList<>();
            Map<String, Limb> limbs = new HashMap<>();
            if (source != null) for (BoneAnimation bone : source.boneAnimations()) {
                if (LIMBS.contains(bone.boneName())) {
                    var r = bone.rotationKeyFrames(); var p = bone.positionKeyFrames();
                    limbs.put(bone.boneName(), new Limb(first(r.xKeyframes()), first(r.yKeyframes()), first(r.zKeyframes()),
                            first(p.xKeyframes()), first(p.yKeyframes()), first(p.zKeyframes())));
                }
            }
            authoredLimbs = Map.copyOf(limbs);
            if (source != null) for (BoneAnimation bone : source.boneAnimations()) {
                if (!LIMBS.contains(bone.boneName())) continue;
                Limb pose = pose(bone.boneName());
                bones.add(new BoneAnimation(bone.boneName(), freeze(bone.rotationKeyFrames(), pose.rx, pose.ry, pose.rz),
                        freeze(bone.positionKeyFrames(), pose.x, pose.y, pose.z), new KeyframeStack<>()));
            }
            if (source != null) for (String name : List.of("Root", "Body", "Neck", "Head"))
                bones.add(new BoneAnimation(name, constant(0), constant(0), constant(1)));
            cachedSource = source;
            cachedPose = new Animation(NAME, 20, Animation.LoopType.LOOP, bones.toArray(BoneAnimation[]::new), NO_EVENTS);
        }
        return cachedPose;
    }

    public static synchronized Limb limb(String name) { return authoredLimbs.get(name); }

    public static boolean isFrontUpper(String name) { return "LForeLeg".equals(name) || "RForeLeg".equals(name); }

    public static boolean isFrontLeg(String name) {
        return isFrontUpper(name) || "LFrontCalf".equals(name) || "RFrontCalf".equals(name)
                || "LFrontHoof".equals(name) || "RFrontHoof".equals(name);
    }

    public static float baseCurl(String name) {
        return isFrontLeg(name) ? PonyFlightMotion.SPRINT_CURL : PonyFlightMotion.BASE_CURL;
    }

    public static synchronized Limb pose(String name) {
        Limb source = authoredLimbs.get(name);
        if (source == null) return null;
        Limb pose = scale(source, baseCurl(name));
        return isFrontUpper(name) ? new Limb(pose.rx - (float) Math.toRadians(PonyFlightMotion.FRONT_LOWER_DEGREES),
                pose.ry, pose.rz, pose.x, pose.y, pose.z) : pose;
    }

    public static synchronized Limb offset(String name, float curlDelta) {
        Limb source = authoredLimbs.get(name);
        if (source == null || isFrontLeg(name) || !Float.isFinite(curlDelta)) return null;
        Limb next = scale(source, curlDelta);
        return Float.isFinite(next.rx) && Float.isFinite(next.ry) && Float.isFinite(next.rz)
                && Float.isFinite(next.x) && Float.isFinite(next.y) && Float.isFinite(next.z) ? next : null;
    }

    private static Limb scale(Limb source, float factor) {
        return new Limb(source.rx * factor, source.ry * factor, source.rz * factor, source.x * factor, source.y * factor, source.z * factor);
    }

    private static KeyframeStack<Keyframe<IValue>> constant(double value) {
        List<Keyframe<IValue>> axis = List.of(new Keyframe<>(20, new Constant(value), new Constant(value)));
        return new KeyframeStack<>(axis, axis, axis);
    }

    private static KeyframeStack<Keyframe<IValue>> freeze(KeyframeStack<Keyframe<IValue>> source, float x, float y, float z) {
        return new KeyframeStack<>(freeze(source.xKeyframes(), x), freeze(source.yKeyframes(), y), freeze(source.zKeyframes(), z));
    }

    private static List<Keyframe<IValue>> freeze(List<Keyframe<IValue>> source, float value) {
        if (source.isEmpty() && value == 0) return List.of();
        return List.of(new Keyframe<>(20, new Constant(value), new Constant(value)));
    }

    private static float first(List<Keyframe<IValue>> source) {
        if (source.isEmpty()) return 0;
        // 零时长首帧的 end 才是源动画在 t=0 的值。
        var first = source.get(0);
        IValue value = first.length() == 0 ? first.endValue() : first.startValue();
        if (!(value instanceof Constant || value instanceof MolangValue molang && molang.isConstant())
                || !Double.isFinite(value.get())) return 0;
        return (float) value.get();
    }
}
