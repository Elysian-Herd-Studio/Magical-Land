package top.elysianherd.magicaland.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;

public final class ManeMirror {
    private ManeMirror() {}

    public static boolean enabled(ModelConfig config, PonyStylePart part) {
        if (config == null || part == null) return false;
        return switch (part) {
            case FRONT_MANE -> config.frontManeMirrored;
            case BACK_MANE -> config.backManeMirrored;
            case TAIL -> config.tailMirrored;
            case EYE -> false;
        };
    }

    public static void set(ModelConfig config, PonyStylePart part, boolean mirrored) {
        switch (part) {
            case FRONT_MANE -> config.frontManeMirrored = mirrored;
            case BACK_MANE -> config.backManeMirrored = mirrored;
            case TAIL -> config.tailMirrored = mirrored;
            case EYE -> {}
        }
    }

    public static boolean rootEnabled(ModelConfig config, String bone) {
        if (config == null) return false;
        return switch (bone) {
            case "FrontMane" -> config.frontManeMirrored;
            case "BackMane" -> config.backManeMirrored;
            case "Tail" -> config.tailMirrored;
            default -> false;
        };
    }

    public static Scope begin(MatrixStack stack, ModelConfig config, String bone) {
        return rootEnabled(config, bone) ? new Scope(stack) : null;
    }

    public static void reflect(MatrixStack stack) {
        // 不用 MatrixStack.scale：其负行列式归一化会翻错法线。
        stack.peek().getPositionMatrix().scale(-1, 1, 1);
        stack.peek().getNormalMatrix().scale(-1, 1, 1);
    }

    public static void emitReversed(GeoQuad quad, Matrix4f matrix, Vector3f normal, VertexConsumer buffer,
            int light, int overlay, float red, float green, float blue, float alpha) {
        var vertices = quad.vertices();
        for (int i = vertices.length - 1; i >= 0; i--) {
            var vertex = vertices[i];
            Vector3f position = matrix.transformPosition(new Vector3f(vertex.position()));
            buffer.vertex(position.x, position.y, position.z, red, green, blue, alpha,
                    vertex.texU(), vertex.texV(), overlay, light, normal.x, normal.y, normal.z);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final MatrixStack stack;
        private boolean closed;
        private Scope(MatrixStack stack) { this.stack = stack; stack.push(); reflect(stack); }
        @Override public void close() { if (!closed) { stack.pop(); closed = true; } }
    }
}
