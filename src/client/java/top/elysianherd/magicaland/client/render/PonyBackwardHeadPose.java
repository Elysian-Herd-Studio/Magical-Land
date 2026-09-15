package top.elysianherd.magicaland.client.render;

import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.GeoBone;
import top.elysianherd.magicaland.client.animation.PonyBackwardLook;

final class PonyBackwardHeadPose {
    record Rotation(float x, float y, float z) {
        Rotation add(float x, float y, float z) { return new Rotation(this.x + x, this.y + y, this.z + z); }
    }
    record Rotations(Rotation neck, Rotation head) {}
    // 保留 backward_walk 第 0 帧作者定下的回头造型，不再播放其中头颈的周期摆动。
    static final Rotation NECK = degrees(-12.49105f, 42.50216f, .01324f);
    static final Rotation HEAD = degrees(97.27481f, 69.8507f, 96.83392f);
    private Object owner;
    private PonyBackwardLook.Phase previousPhase = PonyBackwardLook.Phase.NORMAL;
    private Rotations lastOutput, entry, returningFrom;

    Rotations sample(Object owner, GeoBone neck, GeoBone head, PonyBackwardLook.Frame frame,
            PonyHeadLookMath.Rotation view) {
        if (owner != this.owner) {
            this.owner = owner;
            previousPhase = PonyBackwardLook.Phase.NORMAL;
            lastOutput = entry = returningFrom = null;
        }
        Rotations current = new Rotations(current(neck), current(head).add(view.pitch(), view.yaw(), 0));
        Rotations forward = new Rotations(initial(neck), initial(head).add(view.pitch(), view.yaw(), 0));
        Rotations locked = new Rotations(add(initial(neck), NECK), add(initial(head), HEAD));
        // 前 3 tick 的主控制器仍可能混有旧回头关键帧，不能把它们再加回退场目标。
        Rotations returnTarget = blend(forward, current,
                smooth((frame.progress() * (float) PonyBackwardLook.TURN_TICKS - 3)
                        / (float) (PonyBackwardLook.TURN_TICKS - 3)));
        if (frame.phase() == PonyBackwardLook.Phase.WAITING && previousPhase != frame.phase())
            entry = lastOutput == null ? forward : lastOutput;
        if (frame.phase() == PonyBackwardLook.Phase.RETURNING && previousPhase != frame.phase())
            returningFrom = lastOutput == null ? locked : lastOutput;
        previousPhase = frame.phase();
        lastOutput = switch (frame.phase()) {
            case NORMAL -> current;
            case WAITING -> blend(entry, forward, smooth(frame.progress() * 5));
            case TURNING -> blend(forward, locked, smooth(frame.progress()));
            case HOLDING -> locked;
            case RETURNING -> blend(returningFrom, returnTarget, smooth(frame.progress()));
        };
        return frame.active() ? lastOutput : null;
    }

    static Rotation current(GeoBone bone) { return new Rotation(bone.getRotX(), bone.getRotY(), bone.getRotZ()); }
    static Rotation initial(GeoBone bone) {
        var snapshot = bone.getInitialSnapshot();
        return snapshot == null ? new Rotation(0, 0, 0)
                : new Rotation(snapshot.getRotX(), snapshot.getRotY(), snapshot.getRotZ());
    }
    private static Rotation add(Rotation first, Rotation second) { return first.add(second.x, second.y, second.z); }
    private static Rotation degrees(float x, float y, float z) {
        return new Rotation((float) Math.toRadians(-x), (float) Math.toRadians(-y), (float) Math.toRadians(z));
    }
    static Rotations blend(Rotations from, Rotations to, float amount) {
        return new Rotations(blend(from.neck, to.neck, amount), blend(from.head, to.head, amount));
    }
    static Rotation blend(Rotation from, Rotation to, float amount) {
        if (amount <= 0) return from;
        if (amount >= 1) return to;
        Quaternionf first = new Quaternionf().rotationZYX(from.z, from.y, from.x);
        Quaternionf second = new Quaternionf().rotationZYX(to.z, to.y, to.x);
        Quaternionf q = first.slerp(second, amount).normalize();
        // 显式分解 ZYX，避开 JOML 1.10.5 的 X 分量分母符号问题。
        return new Rotation((float) Math.atan2(2d * (q.w * q.x + q.y * q.z), 1 - 2d * (q.x * q.x + q.y * q.y)),
                (float) Math.asin(Math.max(-1, Math.min(1, 2d * (q.w * q.y - q.z * q.x)))),
                (float) Math.atan2(2d * (q.w * q.z + q.x * q.y), 1 - 2d * (q.y * q.y + q.z * q.z)));
    }
    private static float smooth(float amount) {
        float t = Math.max(0, Math.min(1, amount));
        return t * t * (3 - 2 * t);
    }
}
