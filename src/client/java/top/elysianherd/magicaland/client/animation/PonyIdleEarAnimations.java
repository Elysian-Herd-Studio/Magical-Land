package top.elysianherd.magicaland.client.animation;

import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.keyframe.KeyframeStack;
import software.bernie.geckolib.core.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.SoundKeyframeData;

import java.util.ArrayList;
import java.util.List;

/** 从作者的短抽耳轨道生成轻幅左右变体，原动画文件不变。 */
public final class PonyIdleEarAnimations {
    private static final String PREFIX = "internal.idle_ear.";
    public static final int VARIANTS = 36;
    private static final RawAnimation[] RAW = new RawAnimation[VARIANTS];
    private static final Animation[] CACHE = new Animation[VARIANTS];
    private static final Animation.Keyframes NO_EVENTS = new Animation.Keyframes(new SoundKeyframeData[0],
            new ParticleKeyframeData[0], new CustomInstructionKeyframeData[0]);
    private static Animation cachedSource;
    static {
        for (int i = 0; i < VARIANTS; i++) RAW[i] = RawAnimation.begin().thenPlay(PREFIX + i);
    }

    private PonyIdleEarAnimations() {}

    public static RawAnimation raw(int variant) { return RAW[variant]; }
    public static boolean internal(String name) { return index(name) >= 0; }

    public static synchronized Animation resolve(Animation source, String name) {
        int variant = index(name);
        if (variant < 0) return null;
        if (source != cachedSource) {
            java.util.Arrays.fill(CACHE, null);
            cachedSource = source;
        }
        if (CACHE[variant] == null) CACHE[variant] = build(source, variant);
        return CACHE[variant];
    }

    private static Animation build(Animation source, int variant) {
        String name = PREFIX + variant;
        BoneAnimation original = null;
        if (source != null) for (BoneAnimation bone : source.boneAnimations())
            if ("RightEar".equals(bone.boneName())) original = bone;
        if (original == null || !supported(original)) return empty(name);
        List<Keyframe<IValue>> reference = original.rotationKeyFrames().zKeyframes();
        double start = -1, firstEnd = -1, end = -1, clock = 0;
        for (var frame : reference) {
            if (!finiteConstant(frame.startValue()) || !finiteConstant(frame.endValue())) return empty(name);
            boolean moving = frame.startValue().get() != 0 || frame.endValue().get() != 0;
            if (moving && start < 0) start = clock;
            clock += frame.length();
            if (moving) end = clock;
            if (start >= 0 && firstEnd < 0 && frame.endValue().get() == 0) firstEnd = clock;
        }
        if (start < 0 || firstEnd <= start || end - start > 20) return empty(name);
        int side = variant / 12, pattern = variant % 12;
        boolean doubleFlick = pattern / 6 == 1;
        float gain = .35f + (pattern % 6 / 2) * .1f;
        double stretch = pattern % 2 == 0 ? 1.25 : 1.5;
        double finish = doubleFlick ? end : firstEnd;
        List<BoneAnimation> bones = new ArrayList<>(2);
        if (side == 0 || side == 2) bones.add(copy(original, true, start, finish, stretch, gain,
                side == 2 && pattern % 2 == 1 ? 2 : 0));
        if (side == 1 || side == 2) bones.add(copy(original, false, start, finish, stretch, gain,
                side == 2 && pattern % 2 == 0 ? 2 : 0));
        double length = (finish - start) * stretch + (side == 2 ? 2 : 0);
        return new Animation(name, length, Animation.LoopType.PLAY_ONCE, bones.toArray(BoneAnimation[]::new), NO_EVENTS);
    }

    private static BoneAnimation copy(BoneAnimation original, boolean left, double start, double end,
            double stretch, double gain, double delay) {
        var rotations = original.rotationKeyFrames();
        double mirror = left ? -1 : 1;
        var values = new KeyframeStack<>(cut(rotations.xKeyframes(), start, end, stretch, gain, delay),
                cut(rotations.yKeyframes(), start, end, stretch, gain * mirror, delay),
                cut(rotations.zKeyframes(), start, end, stretch, gain * mirror, delay));
        return new BoneAnimation(left ? "LeftEar" : "RightEar", values, new KeyframeStack<>(), new KeyframeStack<>());
    }

    private static List<Keyframe<IValue>> cut(List<Keyframe<IValue>> source, double start, double end,
            double stretch, double gain, double delay) {
        List<Keyframe<IValue>> result = new ArrayList<>();
        if (delay > 0) result.add(new Keyframe<>(delay, new Constant(0), new Constant(0)));
        double clock = 0;
        for (var frame : source) {
            double begin = clock;
            clock += frame.length();
            if (clock <= start || begin >= end || frame.length() <= 0) continue;
            if (begin < start - .000001 || clock > end + .000001 || !finiteConstant(frame.startValue())
                    || !finiteConstant(frame.endValue()) || frame.easingArgs().stream().anyMatch(value -> !finiteConstant(value)))
                return List.of(new Keyframe<>(1, new Constant(0), new Constant(0)));
            List<IValue> args = frame.easingType() == EasingType.CATMULLROM
                    ? frame.easingArgs().stream().<IValue>map(value -> new Constant(value.get() * gain)).toList()
                    : List.copyOf(frame.easingArgs());
            result.add(new Keyframe<>((clock - begin) * stretch, new Constant(frame.startValue().get() * gain),
                    new Constant(frame.endValue().get() * gain), frame.easingType(), args));
        }
        return List.copyOf(result);
    }

    private static boolean finiteConstant(IValue value) { return value instanceof Constant && Double.isFinite(value.get()); }
    private static boolean supported(BoneAnimation bone) {
        var rotations = bone.rotationKeyFrames();
        for (var list : List.of(rotations.xKeyframes(), rotations.yKeyframes(), rotations.zKeyframes()))
            for (var frame : list)
                if (!Double.isFinite(frame.length()) || frame.length() < 0 || !finiteConstant(frame.startValue())
                        || !finiteConstant(frame.endValue()) || frame.easingArgs().stream().anyMatch(value -> !finiteConstant(value)))
                    return false;
        return true;
    }
    private static Animation empty(String name) { return new Animation(name, 1, Animation.LoopType.PLAY_ONCE, new BoneAnimation[0], NO_EVENTS); }
    private static int index(String name) {
        if (name == null || !name.startsWith(PREFIX)) return -1;
        try {
            int index = Integer.parseInt(name.substring(PREFIX.length()));
            return index >= 0 && index < VARIANTS ? index : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }
}
