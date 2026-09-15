package top.elysianherd.magicaland.client.render;

import java.util.Objects;

/** 掏收时的小幅提示动作，与托蹄姿势切换分开。 */
final class PonyCarryCue {
    static final double DURATION = 6;
    record Pose(float leftPitch, float rightPitch, float headPitch) {
        static final Pose NONE = new Pose(0, 0, 0);
    }
    private Object left, right, mouth;
    private double previous = Double.NaN;
    private double leftStart, rightStart, mouthStart;

    Pose sample(double tick, Object nextLeft, Object nextRight, Object nextMouth) {
        if (!Double.isFinite(tick)) { reset(); return Pose.NONE; }
        if (!Double.isFinite(previous) || tick < previous || tick - previous > 20) {
            leftStart = rightStart = mouthStart = Double.NEGATIVE_INFINITY;
        } else {
            if (!Objects.equals(left, nextLeft)) leftStart = tick;
            if (!Objects.equals(right, nextRight)) rightStart = tick;
            if (!Objects.equals(mouth, nextMouth)) {
                boolean drawing = nextMouth != null;
                boolean stowing = mouth != null && nextMouth == null && nextLeft == null && nextRight == null;
                mouthStart = drawing || stowing ? tick : Double.NEGATIVE_INFINITY;
            } else if (nextMouth == null && (nextLeft != null || nextRight != null)) {
                mouthStart = Double.NEGATIVE_INFINITY;
            }
        }
        left = nextLeft; right = nextRight; mouth = nextMouth; previous = tick;
        return new Pose(pulse(tick - leftStart, 3), pulse(tick - rightStart, 3), pulse(tick - mouthStart, 2.5));
    }
    private static float pulse(double age, double degrees) {
        if (age <= 0 || age >= DURATION) return 0;
        double wave = Math.sin(age / DURATION * Math.PI);
        return (float) (Math.toRadians(degrees) * wave * wave);
    }
    void reset() {
        previous = Double.NaN; left = right = mouth = null;
        leftStart = rightStart = mouthStart = Double.NEGATIVE_INFINITY;
    }
}
