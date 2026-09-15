package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 空手投影：小光核与跟随实际运动的柔焰，共用魔法光的深度合成。 */
public final class MagicFlame {
    static final int MAX_TRACKED = 128, MAX_VERTICES = 36;
    private static final Map<Entity, MagicFlameMotion> MOTION = new WeakHashMap<>();
    private static boolean initialized;
    private static Object world;
    private static Matrix3f worldBasis;
    record Vertex(Vector3f point, float u, float v, float alpha, float whiten) {}
    private record Quad(List<Vertex> vertices, float depth) {}
    private MagicFlame() {}

    static void init() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.world) { clear(); world = client.world; }
            var iterator = MOTION.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Entity source = entry.getKey();
                if (source == null || source.isRemoved() || source.getWorld() != client.world
                        || client.player != null && source.squaredDistanceTo(client.player) > 96 * 96) iterator.remove();
                else entry.getValue().tick(source.age, point(source));
            }
        });
        WorldRenderEvents.START.register(context -> worldBasis = new Matrix3f(context.matrixStack().peek().getPositionMatrix()));
        WorldRenderEvents.END.register(context -> worldBasis = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }
    private static void clear() { MOTION.clear(); world = null; worldBasis = null; }
    private static MagicFlameMotion.Point point(Entity entity) { return new MagicFlameMotion.Point(entity.getX(), entity.getY(), entity.getZ()); }

    public static void render(MatrixStack matrices, Entity source, int color, float tickDelta) {
        if (source == null || source.isRemoved() || !Float.isFinite(tickDelta)) return;
        var client = MinecraftClient.getInstance();
        if (source.getWorld() != client.world || !HornAuraPass.isWorld()) return;
        if (world != client.world) { MOTION.clear(); world = client.world; }
        MagicFlameMotion motion = MOTION.get(source);
        if (motion == null && MOTION.size() < MAX_TRACKED) {
            motion = new MagicFlameMotion(); motion.tick(source.age, point(source)); MOTION.put(source, motion);
        }
        double ticks = source.age + (double) tickDelta;
        var frame = motion == null ? MagicFlameMotion.frame(MagicFlameMotion.Point.ZERO, ticks, source.getId())
                : motion.sample(tickDelta, ticks, source.getId());
        submit(matrices, color, ticks, frame);
    }
    static void stationary(MatrixStack matrices, int color, double ticks, int seed) {
        submit(matrices, color, ticks, MagicFlameMotion.frame(MagicFlameMotion.Point.ZERO, ticks, seed));
    }
    private static void submit(MatrixStack matrices, int color, double ticks, MagicFlameMotion.Frame frame) {
        if (!Double.isFinite(ticks)) return;
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f root = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix3f basis = worldBasis == null ? new Matrix3f(root) : new Matrix3f(worldBasis);
        List<Vertex> vertices = geometry(root, basis, view, frame);
        if (vertices.isEmpty()) return;
        Vector3f normal = new Matrix4f(view).invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        int clock = (int) Math.round(((ticks % 240 + 240) % 240) * 50);
        float red = (color >>> 16 & 255) / 255f, green = (color >>> 8 & 255) / 255f, blue = (color & 255) / 255f;
        HornAuraPass.submit(buffer -> {
            for (Vertex vertex : vertices) {
                float white = vertex.whiten;
                buffer.vertex(vertex.point.x, vertex.point.y, vertex.point.z)
                        .color(red + (1 - red) * white, green + (1 - green) * white, blue + (1 - blue) * white, vertex.alpha)
                        .texture(vertex.u, vertex.v).overlay(clock, 5).light(0, 0)
                        .normal(normal.x, normal.y, normal.z).next();
            }
        });
    }
    static List<Vertex> geometry(Matrix4f root, Matrix3f basis, Matrix4f view, MagicFlameMotion.Frame frame) {
        if (!root.isFinite() || !view.isFinite() || !basis.isFinite() || Math.abs(view.determinant()) < 1e-8) return List.of();
        Matrix4f inverse = new Matrix4f(view).invert();
        Vector3f right = inverse.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f up = inverse.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f origin = root.transformPosition(new Vector3f());
        var offset = frame.tail();
        Vector3f tail = basis.transform(new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z()));
        Vector3f tailView = view.transformDirection(new Vector3f(tail));
        Vector3f across = new Vector3f(-tailView.y, tailView.x, 0);
        if (across.lengthSquared() < 1e-8) across.set(1, 0, 0);
        inverse.transformDirection(across.normalize());
        float scale = Math.min(3, Math.max(.05f, new Matrix3f(root).transform(new Vector3f(1, 0, 0)).length()));
        float radius = frame.radius() * scale;
        List<Quad> quads = new ArrayList<>(9);
        for (int segment = 0; segment < 5; segment++) {
            float a = segment / 5f, b = (segment + 1) / 5f;
            Vector3f start = plume(origin, tail, across, a, frame.sway(), frame.phase());
            Vector3f end = plume(origin, tail, across, b, frame.sway(), frame.phase());
            float widthA = radius * (1 - a) * (1 - .25f * a), widthB = radius * (1 - b) * (1 - .25f * b);
            add(quads, view, List.of(v(start, across, -widthA, 0, a, .42f, 0), v(start, across, widthA, 1, a, .42f, 0),
                    v(end, across, widthB, 1, b, .42f, 0), v(end, across, -widthB, 0, b, .42f, 0)));
        }
        float aligned = tailView.lengthSquared() < 1e-8 ? 0 : Math.abs(tailView.z) / tailView.length();
        for (int slice = 1; slice <= 2; slice++) {
            float alpha = .085f * Math.max(0, (aligned - .55f) / .45f);
            radial(quads, view, new Vector3f(origin).fma(slice * .3f, tail), right, up, radius * (1 - slice * .2f), alpha, .04f);
        }
        radial(quads, view, origin, right, up, radius * 1.65f, .24f, .05f);
        radial(quads, view, origin, right, up, radius * .72f, .70f, .40f);
        quads.sort(Comparator.comparingDouble(Quad::depth));
        List<Vertex> result = new ArrayList<>(MAX_VERTICES);
        for (Quad quad : quads) result.addAll(quad.vertices);
        return List.copyOf(result);
    }
    private static Vector3f plume(Vector3f origin, Vector3f tail, Vector3f across, float t, float sway, float phase) {
        float curl = (float) (Math.sin(t * Math.PI) * (sway + .012 * Math.sin(t * 4 + phase)));
        return new Vector3f(origin).fma(t, tail).fma(curl, across);
    }
    private static Vertex v(Vector3f origin, Vector3f across, float width, float u, float v, float alpha, float white) {
        return new Vertex(new Vector3f(origin).fma(width, across), u, v, alpha, white);
    }
    private static void radial(List<Quad> quads, Matrix4f view, Vector3f center, Vector3f right, Vector3f up, float radius, float alpha, float white) {
        if (alpha < .004f) return;
        List<Vertex> vertices = new ArrayList<>(4);
        for (int corner = 0; corner < 4; corner++) {
            float x = corner == 0 || corner == 3 ? -1 : 1, y = corner < 2 ? -1 : 1;
            vertices.add(new Vertex(new Vector3f(center).fma(radius * x, right).fma(radius * y, up), -1 - (x + 1) * .5f, (y + 1) * .5f, alpha, white));
        }
        add(quads, view, vertices);
    }
    private static void add(List<Quad> quads, Matrix4f view, List<Vertex> vertices) {
        float depth = 0;
        for (Vertex vertex : vertices) { if (!vertex.point.isFinite()) return; depth += view.transformPosition(new Vector3f(vertex.point)).z; }
        quads.add(new Quad(vertices, depth / vertices.size()));
    }
}
