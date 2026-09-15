package top.elysianherd.magicaland.client.render;

import software.bernie.geckolib.cache.object.GeoBone;
import top.elysianherd.magicaland.client.animation.PonyExpressions;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** 适配表情轨道与眼仁注视；所有临时修改在本次面部渲染后恢复。 */
final class PonyFacePose implements AutoCloseable {
    private static final float INWARD_LIMIT = .15f;
    private final Map<String, GeoBone> bones = new HashMap<>();
    private final Map<GeoBone, SavedPose> saved = new IdentityHashMap<>();

    private PonyFacePose(GeoBone root) {
        collect(root);
    }

    static PonyFacePose apply(GeoBone root) {
        PonyFacePose pose = new PonyFacePose(root);
        if (pose.isVisible("Smeile") || pose.isVisible("close") || pose.isVisible("ScrunchedEyes")) {
            // 动作已经指定闭眼时，不让普通眨眼替换其眼型。
            pose.scale("emot", 1);
            pose.scale("shut", 0);
        }

        for (var style : PonyExpressions.eyeStyles().values()) {
            style.bones().forEach((channel, target) -> pose.copy(PonyExpressions.CHANNELS.get(channel), target,
                    channel.endsWith("_pupil")));
        }
        return pose;
    }

    static boolean shouldRender(String boneName, String eyeStyle) {
        return PonyExpressions.shouldRender(boneName, eyeStyle);
    }

    boolean allowsGaze() {
        return isVisible("CommonFace") && !isVisible("Smeile") && !isVisible("close")
                && !isVisible("ScrunchedEyes") && !isVisible("Angry");
    }

    GeoBone pupil(String styleId, boolean left) {
        var styles = PonyExpressions.eyeStyles();
        var style = styleId == null ? styles.get("01") : styles.getOrDefault(styleId, styles.get("01"));
        return bones.get(style.bones().get(left ? "left_pupil" : "right_pupil"));
    }

    void gaze(String styleId, float x, float y) {
        if (!allowsGaze() || !Float.isFinite(x) || !Float.isFinite(y)) return;
        var limits = PonyExpressions.gazeLimits(styleId);
        var bounded = PonyGazeMath.bounded(x, y);
        for (boolean left : new boolean[] {true, false}) {
            GeoBone pupil = pupil(styleId, left);
            if (pupil == null) continue;
            var eye = limits.eye(left);
            boolean clipped = EyeApertures.forPupil(pupil) != null;
            // 未适配裁剪的第三方眼型使用保守的旧式限位。
            float horizontal = clipped ? bounded.x() : left ? Math.max(0, bounded.x()) : Math.min(0, bounded.x());
            float[] growth = clipped ? new float[3] : growth(pupil, left);
            float horizontalLimit = clipped ? eye.outward() : Math.min(.3f, eye.outward());
            // 原始内聚眼位仅留少量向鼻梁的余量，正常向外幅度不变。
            if (clipped && (left ? horizontal < 0 : horizontal > 0))
                horizontalLimit = Math.min(horizontalLimit, INWARD_LIMIT);
            float dx = horizontal * Math.max(0, horizontalLimit - growth[0]);
            float vertical = bounded.y() >= 0 ? (clipped ? eye.up() : Math.min(.15f, eye.up())) - growth[1]
                    : (clipped ? eye.down() : Math.min(.2f, eye.down())) - growth[2];
            float dy = bounded.y() * Math.max(0, vertical);
            save(pupil);
            pupil.updatePosition(pupil.getPosX() + dx, pupil.getPosY() + dy, pupil.getPosZ());
        }
    }

    private static float[] growth(GeoBone pupil, boolean left) {
        float outward = 0, up = 0, down = 0;
        float sx = Math.max(0, pupil.getScaleX() - 1), sy = Math.max(0, pupil.getScaleY() - 1);
        if (sx == 0 && sy == 0) return new float[3];
        for (var cube : pupil.getCubes()) {
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                for (var vertex : quad.vertices()) {
                    var point = vertex.position();
                    float x = point.x() * 16 - pupil.getPivotX();
                    float y = point.y() * 16 - pupil.getPivotY();
                    outward = Math.max(outward, (left ? -x : x) * sx);
                    up = Math.max(up, y * sy);
                    down = Math.max(down, -y * sy);
                }
            }
        }
        return new float[] {outward, up, down};
    }

    private void collect(GeoBone bone) {
        bones.put(bone.getName(), bone);
        for (GeoBone child : bone.getChildBones()) {
            collect(child);
        }
    }

    private boolean isVisible(String name) {
        GeoBone bone = bones.get(name);
        return bone != null && !bone.isHidden()
                && bone.getScaleX() > 0.001f && bone.getScaleY() > 0.001f && bone.getScaleZ() > 0.001f;
    }

    private void scale(String name, float value) {
        GeoBone bone = bones.get(name);
        if (bone == null) return;
        save(bone);
        bone.updateScale(value, value, value);
    }

    private void copy(String sourceName, String targetName, boolean copyPivot) {
        GeoBone source = bones.get(sourceName);
        GeoBone target = bones.get(targetName);
        if (source == null || target == null || source == target) return;
        save(target);
        target.updateScale(source.getScaleX(), source.getScaleY(), source.getScaleZ());
        target.updatePosition(source.getPosX(), source.getPosY(), source.getPosZ());
        target.updateRotation(source.getRotX(), source.getRotY(), source.getRotZ());
        if (copyPivot) {
            // 备用眼仁原 pivot 在原点；缩放时借用同侧眼仁的轴心，避免漂移。
            target.updatePivot(source.getPivotX(), source.getPivotY(), source.getPivotZ());
        }
    }

    private void save(GeoBone bone) {
        saved.computeIfAbsent(bone, SavedPose::capture);
    }

    @Override
    public void close() {
        saved.forEach((bone, pose) -> pose.restore(bone));
    }

    private record SavedPose(float sx, float sy, float sz, float x, float y, float z,
                             float rx, float ry, float rz, float px, float py, float pz,
                             boolean scaleChanged, boolean positionChanged, boolean rotationChanged) {
        static SavedPose capture(GeoBone bone) {
            return new SavedPose(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ(),
                    bone.getPosX(), bone.getPosY(), bone.getPosZ(),
                    bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                    bone.getPivotX(), bone.getPivotY(), bone.getPivotZ(),
                    bone.hasScaleChanged(), bone.hasPositionChanged(), bone.hasRotationChanged());
        }

        void restore(GeoBone bone) {
            bone.updateScale(sx, sy, sz);
            bone.updatePosition(x, y, z);
            bone.updateRotation(rx, ry, rz);
            bone.updatePivot(px, py, pz);
            bone.resetStateChanges();
            if (scaleChanged) bone.markScaleAsChanged();
            if (positionChanged) bone.markPositionAsChanged();
            if (rotationChanged) bone.markRotationAsChanged();
        }
    }
}
