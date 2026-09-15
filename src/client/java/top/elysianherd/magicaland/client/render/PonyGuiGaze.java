package top.elysianherd.magicaland.client.render;

import java.lang.ref.WeakReference;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** 仅在指定界面绘制期间提供鼠标目标，不读取世界实体注视。 */
public final class PonyGuiGaze {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final long ORIGIN = System.nanoTime();
    private static final float TARGET_DISTANCE = 2;

    private PonyGuiGaze() {}

    record Frame(Object owner, Object entity, double mouseX, double mouseY, int width, int height, double ticks) {}

    public static Scope begin(Object owner, Object entity, double mouseX, double mouseY, int width, int height) {
        return beginAt(owner, entity, mouseX, mouseY, width, height, (System.nanoTime() - ORIGIN) / 50_000_000d);
    }

    static Scope beginAt(Object owner, Object entity, double mouseX, double mouseY, int width, int height, double ticks) {
        return new Scope(new Frame(Objects.requireNonNull(owner), entity, mouseX, mouseY, width, height, ticks));
    }

    static Frame current() { return CURRENT.get(); }

    public static final class Scope implements AutoCloseable {
        private final Frame previous;
        private boolean closed;

        private Scope(Frame frame) { previous = CURRENT.get(); CURRENT.set(frame); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static PonyGazeMath.Offset project(Frame frame, Matrix4f eyeToRender, Matrix4f modelView,
            Matrix4f projection, Vector3f eyeCenter) {
        if (frame.width <= 0 || frame.height <= 0 || !Double.isFinite(frame.mouseX) || !Double.isFinite(frame.mouseY)
                || !eyeCenter.isFinite() || !eyeToRender.isFinite() || !modelView.isFinite() || !projection.isFinite())
            return PonyGazeMath.Offset.ZERO;
        Matrix4f eyeToView = new Matrix4f(modelView).mul(eyeToRender);
        Vector3f center = eyeToView.transformPosition(new Vector3f(eyeCenter));
        float scale = (new Vector3f(eyeToView.m00(), eyeToView.m01(), eyeToView.m02()).length()
                + new Vector3f(eyeToView.m10(), eyeToView.m11(), eyeToView.m12()).length()
                + new Vector3f(eyeToView.m20(), eyeToView.m21(), eyeToView.m22()).length()) / 3;
        if (!center.isFinite() || !Float.isFinite(scale) || scale < .0001f) return PonyGazeMath.Offset.ZERO;
        // 在眼前的相机平面取目标，避免同深度横移导致眼神立即顶到极限。
        Vector4f plane = projection.transform(new Vector4f(center.x, center.y, center.z + scale * TARGET_DISTANCE, 1));
        if (!plane.isFinite() || plane.w <= .000001f) return PonyGazeMath.Offset.ZERO;
        Matrix4f inverse = new Matrix4f(projection).invert();
        if (!inverse.isFinite()) return PonyGazeMath.Offset.ZERO;
        Vector4f target = inverse.transform(new Vector4f((float) (2 * frame.mouseX / frame.width - 1),
                (float) (1 - 2 * frame.mouseY / frame.height), plane.z / plane.w, 1));
        if (!target.isFinite() || Math.abs(target.w) < .000001f) return PonyGazeMath.Offset.ZERO;
        Vector3f targetInView = new Vector3f(target.x / target.w, target.y / target.w, target.z / target.w);
        Vector3f delta = new Matrix4f(eyeToView).invert().transformPosition(new Vector3f(targetInView)).sub(eyeCenter);
        if (!delta.isFinite() || delta.lengthSquared() < .000001f || delta.z >= 0) return PonyGazeMath.Offset.ZERO;
        // 仅在眼前后分界附近淡出；不按夹角淡出，避免远处鼠标反而让眼神回中。
        float facing = Math.min(1, -delta.z / .2f);
        float visible = facing * facing * (3 - 2 * facing);
        var offset = PonyGazeMath.project(eyeToView, targetInView, eyeCenter);
        return new PonyGazeMath.Offset(offset.x() * visible, offset.y() * visible);
    }

    static final class Tracker {
        private WeakReference<Object> owner = new WeakReference<>(null), entity = new WeakReference<>(null);
        private final PonyGazeMath.Smoother smoother = new PonyGazeMath.Smoother();
        private double lastTime = Double.NaN;

        PonyGazeMath.Offset step(Frame frame, PonyGazeMath.Offset desired) {
            if (owner.get() != frame.owner || entity.get() != frame.entity || !Double.isFinite(lastTime)
                    || !Double.isFinite(frame.ticks) || frame.ticks < lastTime || frame.ticks - lastTime > 5) {
                smoother.reset();
                owner = new WeakReference<>(frame.owner);
                entity = new WeakReference<>(frame.entity);
            }
            lastTime = frame.ticks;
            return smoother.step(desired, frame.ticks);
        }
    }
}
