package top.elysianherd.magicaland.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import top.elysianherd.magicaland.client.animation.PonyExpressions;

/** 从眼白几何提取固定窗口；不修改模型、UV 或眼仁本身。 */
final class EyeApertures {
    record Eye(EyeApertureClip.Aperture aperture) {}
    private static final Eye UNSUPPORTED = new Eye(null);
    private static final Map<GeoBone, Eye[]> CACHE = new WeakHashMap<>();

    private EyeApertures() {}

    static Eye forPupil(GeoBone pupil) {
        if (pupil.getParent() == null) return null;
        for (var style : PonyExpressions.eyeStyles().values()) {
            boolean left = pupil.getName().equals(style.bones().get("left_pupil"));
            boolean right = pupil.getName().equals(style.bones().get("right_pupil"));
            if (!left && !right) continue;
            GeoBone parent = pupil.getParent();
            if (!parent.getName().equals(style.bones().get("normal"))) return null;
            Eye[] eyes = CACHE.computeIfAbsent(parent, ignored -> new Eye[2]);
            int index = left ? 0 : 1;
            if (eyes[index] == null) {
                try { eyes[index] = create(pupil, left); }
                catch (IllegalArgumentException invalidWindow) { eyes[index] = UNSUPPORTED; }
                if (eyes[index] == null) eyes[index] = UNSUPPORTED;
            }
            return eyes[index] == UNSUPPORTED ? null : eyes[index];
        }
        return null;
    }

    private static Eye create(GeoBone pupil, boolean left) {
        List<List<EyeApertureClip.Point>> regions = new ArrayList<>();
        double lid = upperLidBottom(pupil.getParent(), left);
        for (GeoCube cube : pupil.getParent().getCubes()) {
            Matrix4f transform = cubeTransform(cube);
            for (GeoQuad quad : cube.quads()) {
                if (!whiteFront(quad)) continue;
                List<EyeApertureClip.Point> polygon = new ArrayList<>(4);
                float center = 0;
                for (var vertex : quad.vertices()) {
                    Vector3f p = transform.transformPosition(new Vector3f(vertex.position()));
                    if (!p.isFinite()) return null;
                    polygon.add(new EyeApertureClip.Point(p.x, p.y));
                    center += p.x;
                }
                if ((center < 0) != left) continue;
                // 半睁眼的前置眼皮比白块低，窗口随实际眼皮几何结束。
                if (Double.isFinite(lid)) polygon.replaceAll(p -> new EyeApertureClip.Point(p.x(), Math.min(p.y(), lid)));
                regions.add(polygon);
            }
        }
        if (regions.isEmpty()) return null;
        return new Eye(EyeApertureClip.prepare(regions));
    }

    static boolean whiteFront(GeoQuad quad) {
        return paletteFront(quad, 62, 62.5f);
    }

    private static double upperLidBottom(GeoBone parent, boolean left) {
        double bottom = Double.POSITIVE_INFINITY;
        if (!"Style02CommonFace".equals(parent.getName())) return bottom;
        for (GeoCube cube : parent.getCubes()) for (GeoQuad quad : cube.quads()) {
            if (!paletteFront(quad, 62.5f, 63)) continue;
            Matrix4f transform = cubeTransform(cube);
            double centerX = 0, y = Double.POSITIVE_INFINITY;
            for (var v : quad.vertices()) {
                Vector3f p = transform.transformPosition(new Vector3f(v.position()));
                centerX += p.x; y = Math.min(y, p.y);
            }
            if ((centerX < 0) == left) bottom = Math.min(bottom, y);
        }
        return bottom;
    }

    private static boolean paletteFront(GeoQuad quad, float fromV, float toV) {
        if (quad == null || quad.normal() == null || !quad.normal().isFinite()
                || quad.normal().z > -.9f || quad.vertices().length != 4) return false;
        for (var vertex : quad.vertices()) {
            float u = vertex.texU() * 128, v = vertex.texV() * 128;
            if (!vertex.position().isFinite() || !Float.isFinite(u) || !Float.isFinite(v)
                    || u < -.001f || u > .501f || v < fromV - .001f || v > toV + .001f) return false;
        }
        return true;
    }

    static Matrix4f cubeTransform(GeoCube cube) {
        var p = cube.pivot(); var r = cube.rotation();
        return new Matrix4f().translate((float) p.x / 16, (float) p.y / 16, (float) p.z / 16)
                .rotateZYX((float) r.z, (float) r.y, (float) r.x)
                .translate((float) -p.x / 16, (float) -p.y / 16, (float) -p.z / 16);
    }
}
