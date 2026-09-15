package top.elysianherd.magicaland.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 魔法吃喝只移动当前使用的物品，不改变玩家交互。 */
public final class PonyMagicConsumption {
    public record Pose(float weight, float pitch, float bob) {
        public static final Pose NONE = new Pose(0, 0, 0);
    }
    private final State main = new State(), off = new State();
    private double previous = Double.NaN;

    public void update(double tick, PonyHeldItems.Consumption use, boolean enabled) {
        update(tick, use, enabled, Hand.MAIN_HAND, Hand.OFF_HAND);
    }
    public void update(double tick, PonyHeldItems.Consumption use, boolean enabled, Object mainItem, Object offItem) {
        if (!enabled || !Double.isFinite(tick)) { reset(); return; }
        boolean fresh = !Double.isFinite(previous) || tick < previous || tick - previous > 20;
        float step = fresh ? 1 : (float) Math.min(1, (tick - previous) / 4);
        if (fresh) { main.clear(); off.clear(); }
        main.update(use, Hand.MAIN_HAND, step, mainItem);
        off.update(use, Hand.OFF_HAND, step, offItem);
        previous = tick;
    }
    public Pose pose(boolean mainHand) { return (mainHand ? main : off).pose(); }
    public void reset() { main.clear(); off.clear(); previous = Double.NaN; }

    public static void apply(MatrixStack matrices, Matrix4f anchor, Matrix4f mouth, Pose pose) {
        apply(matrices, anchor, mouth, pose, new Vector3f());
    }
    public static void apply(MatrixStack matrices, Matrix4f anchor, Matrix4f mouth, Pose pose, Vector3f displayTranslation) {
        if (mouth == null || pose.weight() <= 0 || !Float.isFinite(pose.weight())
                || !Float.isFinite(pose.pitch()) || !Float.isFinite(pose.bob()) || !mouth.isFinite()
                || displayTranslation == null || !displayTranslation.isFinite()) return;
        // 嘴部挂点来自本帧已叠加转头和动作的 Head 骨骼。
        Matrix4f target = new Matrix4f(mouth).translate(0, 22.05f / 16 + pose.bob(), -14.5f / 16)
                .rotateX(pose.pitch()).translate(-displayTranslation.x, -displayTranslation.y, -displayTranslation.z);
        Matrix4f moved = blend(matrices.peek().getPositionMatrix(), target, pose.weight());
        Matrix4f movedAnchor = blend(anchor, target, pose.weight());
        if (moved == null || movedAnchor == null) return;
        matrices.peek().getPositionMatrix().set(moved);
        matrices.peek().getNormalMatrix().set(moved).invert().transpose();
        anchor.set(movedAnchor);
    }
    private static Matrix4f blend(Matrix4f from, Matrix4f to, float amount) {
        if (!from.isFinite() || !to.isFinite() || Math.abs(from.determinant()) < 1e-8
                || Math.abs(to.determinant()) < 1e-8) return null;
        float weight = Math.max(0, Math.min(1, amount));
        Vector3f position = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), weight);
        Quaternionf rotation = from.getUnnormalizedRotation(new Quaternionf()).normalize()
                .slerp(to.getUnnormalizedRotation(new Quaternionf()).normalize(), weight);
        return new Matrix4f().translationRotateScale(position, rotation, from.getScale(new Vector3f()));
    }
    private static final class State {
        float weight, ticks;
        Object item;
        UseAction action = UseAction.NONE;
        void update(PonyHeldItems.Consumption use, Hand hand, float step, Object nextItem) {
            if (item != nextItem) { clear(); item = nextItem; }
            boolean active = nextItem != null && use != null && use.hand() == hand
                    && (use.action() == UseAction.EAT || use.action() == UseAction.DRINK);
            if (active) { ticks = Float.isFinite(use.ticks()) ? Math.max(0, use.ticks()) : 0; action = use.action(); }
            weight += Math.max(-step, Math.min(step, (active ? 1 : 0) - weight));
        }
        Pose pose() {
            if (weight <= 0) return Pose.NONE;
            boolean drinking = action == UseAction.DRINK;
            double wave = Math.sin(ticks * Math.PI * 2 / (drinking ? 10 : 8));
            return new Pose(weight, (float) Math.toRadians(drinking ? 42 + 2 * wave : 8 + 3 * wave),
                    (float) wave * (drinking ? .006f : .015f));
        }
        void clear() { weight = ticks = 0; action = UseAction.NONE; item = null; }
    }
}
