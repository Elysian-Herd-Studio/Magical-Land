package top.elysianherd.magicaland.client.animation;

import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.IValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.molang.expressions.MolangValue;

/** Samples the authored poses without changing their resources or shared animation tracks. */
public final class PonyWingFlightAnimations {
    public record Pose(float rx, float ry, float rz, float x, float y, float z, float sx, float sy, float sz) {}
    private static Animation fly, fall, transfer;
    private static Map<String, BoneAnimation> flying = Map.of(), falling = Map.of(), curling = Map.of();

    private PonyWingFlightAnimations() {}

    public static void resolve(Animation nextFly, Animation nextFall, Animation nextTransfer) {
        if (fly != nextFly) { fly = nextFly; flying = bones(nextFly); }
        if (fall != nextFall) { fall = nextFall; falling = bones(nextFall); }
        if (transfer != nextTransfer) { transfer = nextTransfer; curling = bones(nextTransfer); }
    }

    public static Pose wing(String name, double ticks) {
        return sample(flying.get(name), fly == null ? 0 : ticks % Math.max(1, fly.length()));
    }

    public static Pose hoveringBody(double ticks) { return wing("Body", ticks); }

    public static Pose curled(String name, float progress) {
        if (progress < .12f && transfer != null) {
            var value = sample(curling.get(name), Math.max(0, progress / .12) * transfer.length());
            if (value != null) return value;
        }
        return sample(falling.get(name), 0);
    }

    private static Map<String, BoneAnimation> bones(Animation animation) {
        var result = new HashMap<String, BoneAnimation>();
        if (animation != null) for (var bone : animation.boneAnimations()) result.put(bone.boneName(), bone);
        return Map.copyOf(result);
    }

    private static Pose sample(BoneAnimation bone, double ticks) {
        if (bone == null) return null;
        var r = bone.rotationKeyFrames(); var p = bone.positionKeyFrames(); var s = bone.scaleKeyFrames();
        return new Pose(axis(r.xKeyframes(), ticks, 0), axis(r.yKeyframes(), ticks, 0), axis(r.zKeyframes(), ticks, 0),
                axis(p.xKeyframes(), ticks, 0), axis(p.yKeyframes(), ticks, 0), axis(p.zKeyframes(), ticks, 0),
                axis(s.xKeyframes(), ticks, 1), axis(s.yKeyframes(), ticks, 1), axis(s.zKeyframes(), ticks, 1));
    }

    private static float axis(List<Keyframe<IValue>> frames, double ticks, float fallback) {
        double elapsed = 0;
        for (var frame : frames) {
            double length = frame.length();
            if (ticks <= elapsed + length && length > 0) {
                float from = value(frame.startValue(), fallback), to = value(frame.endValue(), from);
                float t = (float) Math.max(0, Math.min(1, (ticks - elapsed) / length));
                return from + (to - from) * (t * t * (3 - 2 * t));
            }
            elapsed += length;
        }
        return frames.isEmpty() ? fallback : value(frames.get(frames.size() - 1).endValue(), fallback);
    }

    private static float value(IValue value, float fallback) {
        if (!(value instanceof Constant || value instanceof MolangValue molang && molang.isConstant())) return fallback;
        double result = value.get();
        return Double.isFinite(result) ? (float) result : fallback;
    }
}
