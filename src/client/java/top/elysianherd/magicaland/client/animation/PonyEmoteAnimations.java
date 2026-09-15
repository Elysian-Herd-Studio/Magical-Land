package top.elysianherd.magicaland.client.animation;

import java.util.Arrays;
import java.util.List;
import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.keyframe.KeyframeStack;
import software.bernie.geckolib.core.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.SoundKeyframeData;

/** 适配作者动作的运行时轨道，保留原动画资源。 */
public final class PonyEmoteAnimations {
    public static final String OPEN_EYES = "internal.emote.open_eyes";
    private static final Animation OPEN_EYE_ANIMATION = new Animation(OPEN_EYES, 1, Animation.LoopType.LOOP,
            new BoneAnimation[] {scale("emot", 1), scale("shut", 0), scale("leye", 1), scale("reye", 1)},
            new Animation.Keyframes(new SoundKeyframeData[0], new ParticleKeyframeData[0], new CustomInstructionKeyframeData[0]));
    private static Animation cachedSource, cachedBallet;

    private PonyEmoteAnimations() {}

    public static Animation openEyes() { return OPEN_EYE_ANIMATION; }

    public static double phaseTicks(String action, double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks)) return 0;
        double phase = Math.max(0, elapsedTicks - 3);
        return "ballet".equals(action) ? phase % 160 : phase;
    }

    private static BoneAnimation scale(String name, double value) {
        List<Keyframe<IValue>> frames = List.of(new Keyframe<>(1, new Constant(value), new Constant(value)));
        return new BoneAnimation(name, new KeyframeStack<>(), new KeyframeStack<>(), new KeyframeStack<>(frames, frames, frames));
    }

    public static synchronized Animation resolveBallet(Animation source) {
        if (source == null) {
            cachedSource = cachedBallet = null;
            return null;
        }
        if (!"Ballet".equals(source.name())) return source;
        if (source != cachedSource) {
            BoneAnimation[] bones = Arrays.stream(source.boneAnimations())
                    .filter(bone -> !"Style05BackMane01".equals(bone.boneName()))
                    .toArray(BoneAnimation[]::new);
            cachedSource = source;
            cachedBallet = bones.length == source.boneAnimations().length ? source
                    : new Animation(source.name(), source.length(), source.loopType(), bones, source.keyFrames());
        }
        return cachedBallet;
    }
}
