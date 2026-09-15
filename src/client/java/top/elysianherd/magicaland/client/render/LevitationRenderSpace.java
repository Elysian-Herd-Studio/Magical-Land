package top.elysianherd.magicaland.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** World-relative coordinates use the actual render matrix, including camera effects. */
public final class LevitationRenderSpace {
    private LevitationRenderSpace() {}

    public static Vector3f anchor(Matrix4f worldFrame, Matrix4f itemFrame) {
        Vector3f rendered = itemFrame.transformPosition(new Vector3f(), new Vector3f());
        return new Matrix4f(worldFrame).invert().transformPosition(rendered);
    }
}
