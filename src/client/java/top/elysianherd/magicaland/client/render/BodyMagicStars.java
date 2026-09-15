package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

public final class BodyMagicStars {
    private static final Consumer<VertexConsumer> EMPTY = ignored -> { };
    private static final Map<GeoBone, Bounds> BOUNDS = new WeakHashMap<>();
    private record Bounds(Vector3f min, Vector3f max) { }
    private record Star(Vector3f position, float radius, float alpha) { }

    private BodyMagicStars() { }

    public static Consumer<VertexConsumer> capture(MatrixStack preparedBodyPose, GeoBone body,
                                                   int color, double ticks, int seed, float amount) {
        if (preparedBodyPose == null || body == null || body.isHidden() || !Double.isFinite(ticks)
                || !Float.isFinite(amount) || amount <= .001f) return EMPTY;
        Bounds bounds = BOUNDS.computeIfAbsent(body, BodyMagicStars::bounds);
        if (bounds == null) return EMPTY;
        Matrix4f root = new Matrix4f(preparedBodyPose.peek().getPositionMatrix());
        Matrix4f inverseView = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        Vector3f right = inverseView.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f up = inverseView.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f normal = inverseView.transformDirection(new Vector3f(0, 0, 1)).normalize();
        if (!root.isFinite() || !right.isFinite() || !up.isFinite() || !normal.isFinite()) return EMPTY;
        Vector3f center = bounds.min.add(bounds.max, new Vector3f()).mul(.5f);
        Vector3f size = bounds.max.sub(bounds.min, new Vector3f());
        float extent = Math.max(size.x, Math.max(size.y, size.z));
        float halfX = Math.max(size.x * .5f, extent * .08f), halfZ = Math.max(size.z * .5f, extent * .08f);
        float margin = Math.max(.02f, extent * .08f);
        float spread = Math.max(.5f, Math.min(3, extent / .32f));
        float scale = (root.transformDirection(new Vector3f(1, 0, 0)).length()
                + root.transformDirection(new Vector3f(0, 1, 0)).length()
                + root.transformDirection(new Vector3f(0, 0, 1)).length()) / 3;
        List<Star> stars = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) {
            var star = MagicSparkles.sampleFalling(ticks, seed, slot);
            if (star == null) continue;
            float length = Math.max(1e-5f, (float) Math.hypot(star.x(), star.z()));
            float dx = star.x() / length, dz = star.z() / length;
            float edge = Math.min(halfX / Math.max(Math.abs(dx), 1e-5f), halfZ / Math.max(Math.abs(dz), 1e-5f));
            Vector3f position = root.transformPosition(new Vector3f(center).add(
                    dx * (edge + margin), star.y() * spread, dz * (edge + margin)));
            stars.add(new Star(position, star.radius() * spread * scale * .75f,
                    star.alpha() * .8f * Math.min(1, amount)));
        }
        if (stars.isEmpty()) return EMPTY;
        float red = (color >>> 16 & 255) / 255f, green = (color >>> 8 & 255) / 255f, blue = (color & 255) / 255f;
        int clock = (int) Math.round((ticks - Math.floor(ticks / 240) * 240) * 50);
        return buffer -> {
            for (Star star : stars) for (int corner = 0; corner < 4; corner++) {
                float x = corner == 0 || corner == 3 ? -1 : 1, y = corner < 2 ? -1 : 1;
                Vector3f point = new Vector3f(star.position).fma(x * star.radius, right).fma(y * star.radius, up);
                buffer.vertex(point.x, point.y, point.z).color(red, green, blue, star.alpha)
                        .texture((x + 1) * .5f, (y + 1) * .5f).overlay(clock, 1).light(0, 0)
                        .normal(normal.x, normal.y, normal.z).next();
            }
        };
    }

    private static Bounds bounds(GeoBone body) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (var cube : body.getCubes()) {
            Vector3f[] corners = HornGlowGeometry.bounds(cube);
            if (!corners[0].isFinite() || !corners[1].isFinite()) continue;
            MatrixStack cubePose = new MatrixStack();
            RenderUtils.translateToPivotPoint(cubePose, cube);
            RenderUtils.rotateMatrixAroundCube(cubePose, cube);
            RenderUtils.translateAwayFromPivotPoint(cubePose, cube);
            for (int i = 0; i < 8; i++) {
                Vector3f point = new Vector3f((i & 1) == 0 ? corners[0].x : corners[1].x,
                        (i & 2) == 0 ? corners[0].y : corners[1].y, (i & 4) == 0 ? corners[0].z : corners[1].z);
                cubePose.peek().getPositionMatrix().transformPosition(point);
                min.min(point); max.max(point);
            }
        }
        return min.isFinite() && max.isFinite() && max.distanceSquared(min) > 1e-10f ? new Bounds(min, max) : null;
    }
}
