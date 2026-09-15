package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class LevitationTrail {
    public static final LevitationTrail EMPTY = new LevitationTrail(List.of(), 0);
    static final int MAX_SUPPORT_SLICES = 4;
    private final List<Node> nodes;
    private final float width;
    private final Function<Vector3f, LevitationTrail> capture;
    private record Node(Vector3f point, float alpha) {}
    private record Corner(Vector3f point, float u, float v, float alpha) {}
    private record Quad(Corner a, Corner b, Corner c, Corner d, float depth) {}

    private LevitationTrail(List<Node> nodes, float width) { this.nodes = nodes; this.width = width; this.capture = null; }

    private LevitationTrail(Function<Vector3f, LevitationTrail> capture) {
        this.nodes = List.of(); this.width = 0; this.capture = capture;
    }

    static LevitationTrail deferred(Function<Vector3f, LevitationTrail> capture) { return new LevitationTrail(capture); }
    LevitationTrail atCenter(Vector3f center) { return capture == null ? this : capture.apply(center); }

    static LevitationTrail create(List<LevitationMotion.TrailPoint> points, Matrix4f frame,
            LevitationMotion.Point origin, float width) {
        if (points.size() < 2 || !Float.isFinite(width) || width <= 0) return EMPTY;
        List<Node> nodes = new ArrayList<>();
        for (int i = Math.max(0, points.size() - LevitationMotion.MAX_TRAIL_POINTS); i < points.size(); i++) {
            var point = points.get(i);
            var relative = point.position().subtract(origin);
            Vector3f transformed = frame.transformPosition(new Vector3f((float) relative.x(), (float) relative.y(), (float) relative.z()));
            if (!transformed.isFinite()) return EMPTY;
            if (!Float.isFinite(point.alpha())) return EMPTY;
            nodes.add(new Node(transformed, Math.max(0, Math.min(LevitationMotion.TRAIL_ALPHA, point.alpha()))));
        }
        return new LevitationTrail(List.copyOf(nodes), width);
    }

    public boolean isEmpty() { return nodes.size() < 2; }

    void render(VertexConsumer buffer, int color, int clock) {
        render(buffer, color, clock, new Matrix4f(RenderSystem.getModelViewMatrix()));
    }

    void render(VertexConsumer buffer, int color, int clock, Matrix4f view) {
        if (isEmpty() || !view.isFinite() || Math.abs(view.determinant()) < 1e-8f) return;
        Matrix4f inverse = new Matrix4f(view).invert();
        Vector3f normal = inverse.transformDirection(new Vector3f(0, 0, 1)).normalize();
        Vector3f[] viewed = new Vector3f[nodes.size()];
        float[] distances = new float[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            viewed[i] = view.transformPosition(new Vector3f(nodes.get(i).point));
            if (i > 0) distances[i] = distances[i - 1] + nodes.get(i).point.distance(nodes.get(i - 1).point);
        }
        float length = distances[distances.length - 1];
        if (length < 1e-6f) return;
        List<Quad> quads = new ArrayList<>(nodes.size() - 1 + MAX_SUPPORT_SLICES);
        for (int i = 1; i < nodes.size(); i++) {
            Node a = nodes.get(i - 1), b = nodes.get(i);
            Vector3f direction = new Vector3f(viewed[i]).sub(viewed[i - 1]);
            if (direction.lengthSquared() < 1e-12f) continue;
            Vector3f facing = facing(new Vector3f(viewed[i - 1]).add(viewed[i]).mul(.5f));
            float support = supportWeight(direction, facing);
            Vector3f across = new Vector3f(facing).cross(direction);
            if (across.lengthSquared() < 1e-10f) continue;
            inverse.transformDirection(across.normalize().mul(width));
            float ribbonGain = 1 - .6f * support;
            float alphaA = i == 1 ? 0 : a.alpha * ribbonGain;
            float widthA = i == 1 ? .18f : taper(a.alpha), widthB = taper(b.alpha);
            float uA = distances[i - 1] / length, uB = distances[i] / length;
            quads.add(new Quad(corner(a.point, across, -widthA, uA, 0, alphaA),
                    corner(a.point, across, widthA, uA, 1, alphaA),
                    corner(b.point, across, widthB, uB, 1, b.alpha * ribbonGain),
                    corner(b.point, across, -widthB, uB, 0, b.alpha * ribbonGain),
                    (viewed[i - 1].z + viewed[i].z) * .5f));
        }
        // 沿视轴时用少量柔光截面补体积；四片共用不超过一次光带的透明度预算。
        for (int slice = 1; slice <= MAX_SUPPORT_SLICES; slice++) {
            float distance = length * slice / MAX_SUPPORT_SLICES;
            int end = 1;
            while (end < distances.length - 1 && distances[end] < distance) end++;
            float segment = distances[end] - distances[end - 1];
            if (segment < 1e-6f) continue;
            float t = Math.max(0, Math.min(1, (distance - distances[end - 1]) / segment));
            Node a = nodes.get(end - 1), b = nodes.get(end);
            Vector3f center = new Vector3f(a.point).lerp(b.point, t);
            Vector3f centerView = view.transformPosition(new Vector3f(center));
            float support = supportWeight(new Vector3f(viewed[end]).sub(viewed[end - 1]), facing(centerView));
            float alpha = (end == 1 ? 0 : a.alpha) * (1 - t) + b.alpha * t;
            float budget = alpha * .85f / MAX_SUPPORT_SLICES * support;
            if (budget <= .004f) continue;
            float radius = width * taper(alpha);
            Vector3f right = inverse.transformDirection(new Vector3f(radius, 0, 0));
            Vector3f up = inverse.transformDirection(new Vector3f(0, radius, 0));
            quads.add(new Quad(radial(center, right, up, -1, -1, budget),
                    radial(center, right, up, 1, -1, budget),
                    radial(center, right, up, 1, 1, budget),
                    radial(center, right, up, -1, 1, budget), centerView.z));
        }
        // Fabulous 的透明目标写自身深度，按远到近绘制避免截面互相吞掉。
        quads.sort(Comparator.comparingDouble(Quad::depth));
        for (Quad quad : quads) {
            vertex(buffer, quad.a, color, clock, normal);
            vertex(buffer, quad.b, color, clock, normal);
            vertex(buffer, quad.c, color, clock, normal);
            vertex(buffer, quad.d, color, clock, normal);
        }
    }

    private static Vector3f facing(Vector3f position) {
        return position.lengthSquared() < 1e-10f ? new Vector3f(0, 0, 1) : new Vector3f(position).negate().normalize();
    }

    private static float supportWeight(Vector3f direction, Vector3f facing) {
        if (direction.lengthSquared() < 1e-12f) return 0;
        float alignment = Math.abs(direction.dot(facing)) / direction.length();
        float blend = Math.max(0, Math.min(1, (alignment - .55f) / .4f));
        return blend * blend * (3 - 2 * blend);
    }

    private static float taper(float alpha) { return Math.max(.18f, alpha / LevitationMotion.TRAIL_ALPHA); }

    private static Corner corner(Vector3f point, Vector3f across, float side, float u, float v, float alpha) {
        return new Corner(new Vector3f(point).fma(side, across), u, v, alpha);
    }

    private static Corner radial(Vector3f center, Vector3f right, Vector3f up, float x, float y, float alpha) {
        return new Corner(new Vector3f(center).fma(x, right).fma(y, up), -1 - (x + 1) * .5f, (y + 1) * .5f, alpha);
    }

    private static void vertex(VertexConsumer buffer, Corner corner, int color, int clock, Vector3f normal) {
        buffer.vertex(corner.point.x, corner.point.y, corner.point.z)
                .color((color >>> 16 & 255) / 255f, (color >>> 8 & 255) / 255f, (color & 255) / 255f, corner.alpha)
                .texture(corner.u, corner.v).overlay(clock, 3).light(0xF000F0)
                .normal(normal.x, normal.y, normal.z).next();
    }
}
