package top.elysianherd.magicaland.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.util.RenderUtils;

final class EyeApertureRender {
    private final EyeApertures.Eye eye;
    private final Matrix4f parentToRender, boneToParent;

    private EyeApertureRender(MatrixStack parent, GeoBone bone, EyeApertures.Eye eye) {
        this.eye = eye;
        parentToRender = new Matrix4f(parent.peek().getPositionMatrix());
        MatrixStack local = new MatrixStack();
        RenderUtils.prepMatrixForBone(local, bone);
        boneToParent = new Matrix4f(local.peek().getPositionMatrix());
    }

    static EyeApertureRender begin(MatrixStack parent, GeoBone bone) {
        var eye = EyeApertures.forPupil(bone);
        return eye == null ? null : new EyeApertureRender(parent, bone, eye);
    }

    void cube(MatrixStack stack, GeoCube cube, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        Matrix4f local = new Matrix4f(boneToParent).mul(EyeApertures.cubeTransform(cube));
        RenderUtils.translateToPivotPoint(stack, cube);
        RenderUtils.rotateMatrixAroundCube(stack, cube);
        RenderUtils.translateAwayFromPivotPoint(stack, cube);
        for (var quad : cube.quads()) {
            if (quad == null) continue;
            Vector3f normal = stack.peek().getNormalMatrix().transform(new Vector3f(quad.normal()));
            RenderUtils.fixInvertedFlatCube(cube, normal);
            var vertices = quad.vertices();
            if (vertices.length != 4) continue;
            EyeApertureClip.Vertex[] input = new EyeApertureClip.Vertex[4];
            for (int i = 0; i < 4; i++) {
                var v = vertices[i];
                var p = local.transformPosition(new Vector3f(v.position()));
                input[i] = new EyeApertureClip.Vertex(p.x, p.y, p.z, v.texU(), v.texV());
            }
            for (var polygon : eye.aperture().clipQuad(input[0], input[1], input[2], input[3])) {
                if (polygon.size() == 4) {
                    for (var v : polygon) vertex(v, normal, buffer, light, overlay, red, green, blue, alpha);
                } else for (int i = 1; i + 1 < polygon.size(); i++) {
                    vertex(polygon.get(0), normal, buffer, light, overlay, red, green, blue, alpha);
                    vertex(polygon.get(i), normal, buffer, light, overlay, red, green, blue, alpha);
                    vertex(polygon.get(i + 1), normal, buffer, light, overlay, red, green, blue, alpha);
                    vertex(polygon.get(i + 1), normal, buffer, light, overlay, red, green, blue, alpha);
                }
            }
        }
    }

    private void vertex(EyeApertureClip.Vertex v, Vector3f n, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        Vector3f p = parentToRender.transformPosition(new Vector3f((float) v.x(), (float) v.y(), (float) v.z()));
        buffer.vertex(p.x, p.y, p.z, red, green, blue, alpha, (float) v.u(), (float) v.v(), overlay, light, n.x, n.y, n.z);
    }
}
