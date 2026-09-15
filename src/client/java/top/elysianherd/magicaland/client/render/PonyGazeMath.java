package top.elysianherd.magicaland.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PonyGazeMath {
    record Offset(float x, float y) {
        static final Offset ZERO = new Offset(0, 0);
    }

    private PonyGazeMath() {}

    static Offset project(Matrix4f eyeToRender, Vector3f targetInRender, Vector3f eyeCenter) {
        if (!Float.isFinite(eyeToRender.determinant()) || Math.abs(eyeToRender.determinant()) < 0.000001f) return Offset.ZERO;
        Vector3f delta = new Matrix4f(eyeToRender).invert().transformPosition(new Vector3f(targetInRender)).sub(eyeCenter);
        if (!delta.isFinite() || delta.lengthSquared() < 0.000001f || delta.z >= 0) return Offset.ZERO;
        double yaw = Math.atan2(delta.x, -delta.z);
        double pitch = Math.atan2(delta.y, Math.hypot(delta.x, delta.z));
        // GeckoLib 的 position.x 在渲染时取反，y 不取反。
        return bounded((float) (-yaw / Math.toRadians(45)), (float) (pitch / Math.toRadians(22)));
    }

    static Offset bounded(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return Offset.ZERO;
        x = Math.max(-1, Math.min(1, x));
        y = Math.max(-1, Math.min(1, y));
        float length = (float) Math.hypot(x, y);
        return length > 1 ? new Offset(x / length, y / length) : new Offset(x, y);
    }

    static final class Smoother {
        private float x;
        private float y;
        private double lastTime = Double.NaN;

        Offset step(Offset target, double time) {
            if (!Double.isFinite(time)) { reset(); return Offset.ZERO; }
            if (Double.isNaN(lastTime) || time < lastTime) {
                x = 0;
                y = 0;
                lastTime = time;
                return Offset.ZERO;
            }
            double elapsed = Math.min(5, time - lastTime);
            lastTime = time;
            float weight = (float) -Math.expm1(-elapsed / 2.5);
            Offset boundedTarget = bounded(target.x, target.y);
            x += (boundedTarget.x - x) * weight;
            y += (boundedTarget.y - y) * weight;
            return new Offset(x, y);
        }

        void reset() {
            x = 0;
            y = 0;
            lastTime = Double.NaN;
        }
    }
}
