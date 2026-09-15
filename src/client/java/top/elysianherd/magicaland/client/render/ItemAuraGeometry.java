package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ItemAuraGeometry {
    static final int MAX_VERTICES = 8192;
    static final float EXPANSION_GAIN = 1.65f;
    static final float[] OPACITY = {0.12f, 0.095f, 0.072f, 0.050f, 0.030f, 0.016f};
    record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, float alpha) {}
    record Batch(Identifier texture, List<Vertex> vertices) {}

    private ItemAuraGeometry() {}

    static final class Capture {
        private final Matrix4f root, inverse;
        private final Matrix3f normals, inverseNormals;
        private final Map<Identifier, List<Vertex>> vertices = new LinkedHashMap<>();
        private int count;

        Capture(MatrixStack.Entry pose) {
            root = new Matrix4f(pose.getPositionMatrix());
            inverse = new Matrix4f(root).invert();
            normals = new Matrix3f(pose.getNormalMatrix());
            inverseNormals = new Matrix3f(normals).invert();
        }

        VertexConsumer wrap(VertexConsumer original, Identifier texture) {
            List<Vertex> target = vertices.computeIfAbsent(texture, ignored -> new ArrayList<>());
            return new VertexConsumer() {
                private float x, y, z, u, v, nx, ny = 1, nz, alpha = 1;
                @Override public VertexConsumer vertex(double x, double y, double z) {
                    original.vertex(x, y, z);
                    this.x = (float) x; this.y = (float) y; this.z = (float) z;
                    return this;
                }
                @Override public VertexConsumer color(int red, int green, int blue, int alpha) {
                    original.color(red, green, blue, alpha);
                    this.alpha = alpha / 255f;
                    return this;
                }
                @Override public VertexConsumer texture(float u, float v) {
                    original.texture(u, v);
                    this.u = u; this.v = v;
                    return this;
                }
                @Override public VertexConsumer overlay(int u, int v) { original.overlay(u, v); return this; }
                @Override public VertexConsumer light(int u, int v) { original.light(u, v); return this; }
                @Override public VertexConsumer normal(float x, float y, float z) {
                    original.normal(x, y, z);
                    nx = x; ny = y; nz = z;
                    return this;
                }
                @Override public void next() {
                    original.next();
                    if (count >= MAX_VERTICES) return;
                    Vector3f point = inverse.transformPosition(new Vector3f(x, y, z));
                    Vector3f normal = inverseNormals.transform(new Vector3f(nx, ny, nz));
                    if (!point.isFinite() || !normal.isFinite()) return;
                    if (normal.lengthSquared() < 1e-10f) normal.set(0, 1, 0); else normal.normalize();
                    target.add(new Vertex(point.x, point.y, point.z, u, v, normal.x, normal.y, normal.z, alpha));
                    count++;
                }
                @Override public void fixedColor(int red, int green, int blue, int alpha) {
                    original.fixedColor(red, green, blue, alpha);
                    this.alpha = alpha / 255f;
                }
                @Override public void unfixColor() { original.unfixColor(); alpha = 1; }
            };
        }

        Mesh finish() {
            List<Batch> batches = new ArrayList<>();
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (var entry : vertices.entrySet()) {
                List<Vertex> source = entry.getValue();
                List<Vertex> complete = List.copyOf(source.subList(0, source.size() / 4 * 4));
                if (complete.isEmpty()) continue;
                for (Vertex vertex : complete) {
                    min.min(new Vector3f(vertex.x, vertex.y, vertex.z));
                    max.max(new Vector3f(vertex.x, vertex.y, vertex.z));
                }
                batches.add(new Batch(entry.getKey(), complete));
            }
            return new Mesh(root, normals, List.copyOf(batches), min, max);
        }
    }

    static final class Mesh {
        final List<Batch> batches;
        final Vector3f min, max;
        private final Matrix4f root;
        private final Matrix3f normals;
        private final Vector3f center;
        private final float extent, rootScale;

        Mesh(Matrix4f root, Matrix3f normals, List<Batch> batches, Vector3f min, Vector3f max) {
            this.root = new Matrix4f(root);
            this.normals = new Matrix3f(normals);
            this.batches = batches;
            this.min = min; this.max = max;
            center = min.add(max, new Vector3f()).mul(0.5f);
            Vector3f size = max.sub(min, new Vector3f());
            extent = Math.max(size.x, Math.max(size.y, size.z));
            rootScale = (root.transformDirection(new Vector3f(1, 0, 0)).length()
                    + root.transformDirection(new Vector3f(0, 1, 0)).length()
                    + root.transformDirection(new Vector3f(0, 0, 1)).length()) / 3;
        }

        boolean isEmpty() { return batches.isEmpty() || !Float.isFinite(extent) || extent < 1e-5f; }

        Vector3f centerInRender() { return root.transformPosition(new Vector3f(center)); }

        void render(Batch batch, VertexConsumer buffer, int color, int clock) {
            render(batch,buffer,color,clock,OPACITY,EXPANSION_GAIN);
        }

        void render(Batch batch, VertexConsumer buffer, int color, int clock,float[] opacity,float expansion) {
            float red = (color >>> 16 & 255) / 255f, green = (color >>> 8 & 255) / 255f, blue = (color & 255) / 255f;
            for (int layer = 0; layer < opacity.length; layer++) for (Vertex vertex : batch.vertices) {
                Vector3f point = expanded(vertex, center, extent, layer,expansion);
                root.transformPosition(point);
                Vector3f normal = normals.transform(new Vector3f(vertex.nx, vertex.ny, vertex.nz)).normalize();
                buffer.vertex(point.x, point.y, point.z).color(red, green, blue, opacity[layer] * vertex.alpha)
                        .texture(vertex.u, vertex.v).overlay(clock, 2)
                        .light(max.x - min.x >= max.z - min.z ? height(vertex.x, min.x, max.x) : height(vertex.z, min.z, max.z),
                                height(vertex.y, min.y, max.y))
                        .normal(normal.x, normal.y, normal.z).next();
            }
        }

        void stars(VertexConsumer buffer, int color, double ticks, int seed, int clock) {
            if (isEmpty()) return;
            float red = (color >>> 16 & 255) / 255f, green = (color >>> 8 & 255) / 255f, blue = (color & 255) / 255f;
            float spread = Math.max(0.5f, Math.min(3, extent / 0.32f));
            Matrix4f viewInverse = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
            Vector3f right = viewInverse.transformDirection(new Vector3f(1, 0, 0)).normalize();
            Vector3f up = viewInverse.transformDirection(new Vector3f(0, 1, 0)).normalize();
            Vector3f normal = viewInverse.transformDirection(new Vector3f(0, 0, 1)).normalize();
            float halfX = Math.max((max.x - min.x) * 0.5f, extent * 0.08f);
            float halfZ = Math.max((max.z - min.z) * 0.5f, extent * 0.08f);
            float margin = Math.max(0.02f, extent * 0.08f);
            for (int slot = 0; slot < 4; slot++) {
                var star = MagicSparkles.sampleFalling(ticks, seed, slot);
                if (star == null) continue;
                float directionLength = Math.max(1e-5f, (float) Math.hypot(star.x(), star.z()));
                float directionX = star.x() / directionLength, directionZ = star.z() / directionLength;
                float edge = Math.min(halfX / Math.max(Math.abs(directionX), 1e-5f),
                        halfZ / Math.max(Math.abs(directionZ), 1e-5f));
                Vector3f position = root.transformPosition(new Vector3f(center).add(
                        directionX * (edge + margin), star.y() * spread, directionZ * (edge + margin)));
                float radius = star.radius() * spread * rootScale * 0.75f;
                for (int corner = 0; corner < 4; corner++) {
                    float x = corner == 0 || corner == 3 ? -1 : 1, y = corner < 2 ? -1 : 1;
                    Vector3f point = new Vector3f(position).fma(x * radius, right).fma(y * radius, up);
                    buffer.vertex(point.x, point.y, point.z).color(red, green, blue, star.alpha() * 0.8f)
                            .texture((x + 1) * 0.5f, (y + 1) * 0.5f).overlay(clock, 1).light(0, 0)
                            .normal(normal.x, normal.y, normal.z).next();
                }
            }
        }
    }

    static Vector3f expanded(Vertex vertex, Vector3f center, float extent, int layer) {
        return expanded(vertex,center,extent,layer,EXPANSION_GAIN);
    }

    static Vector3f expanded(Vertex vertex, Vector3f center, float extent, int layer,float expansion) {
        float dilation = 1 + (0.025f + layer * 0.024f) * expansion;
        float push = extent * (0.007f + layer * 0.004f) * expansion;
        return new Vector3f(vertex.x, vertex.y, vertex.z).sub(center).mul(dilation).add(center)
                .add(vertex.nx * push, vertex.ny * push, vertex.nz * push);
    }

    static int height(float y, float min, float max) {
        if (!Float.isFinite(y) || max - min < 1e-6f) return 16384;
        return Math.round(Math.max(0, Math.min(1, (y - min) / (max - min))) * 32767);
    }
}
