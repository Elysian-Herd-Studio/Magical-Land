package top.elysianherd.magicaland.client.animation;

import java.util.Set;
import java.util.Map;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;

/** 将挥手中相对实体的腿部旋转转换为 Gecko 使用的父级局部旋转。 */
public final class PonyEmotePose implements AutoCloseable {
    private static final Set<String> WORLD_ROTATION_LEGS = Set.of("LForeLeg", "RForeLeg", "LHindLeg", "RHindLeg");
    private final GeoBone bone;
    private final float x, y, z;
    private final boolean rotationChanged, positionChanged, scaleChanged;

    private PonyEmotePose(GeoBone bone) {
        this.bone = bone;
        x = bone.getRotX(); y = bone.getRotY(); z = bone.getRotZ();
        rotationChanged = bone.hasRotationChanged();
        positionChanged = bone.hasPositionChanged();
        scaleChanged = bone.hasScaleChanged();
    }

    public static PonyEmotePose apply(GeoBone bone, String action) {
        if (!"wave".equals(action) || !WORLD_ROTATION_LEGS.contains(bone.getName())) return null;
        Quaternionf parent = parentRotation(bone.getParent());
        Quaternionf rotation = parent.invert().mul(new Quaternionf().rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX())).normalize();
        if (!rotation.isFinite()) return null;
        PonyEmotePose pose = new PonyEmotePose(bone);
        setRotation(bone, rotation);
        return pose;
    }

    static Quaternionf localRotation(GeoBone bone, Map<String, BoneSnapshot> snapshots) {
        return parentRotation(bone.getParent(), snapshots).invert().mul(snapshotRotation(bone, snapshots)).normalize();
    }

    static void setRotation(GeoBone bone, Quaternionf rotation) {
        bone.updateRotation((float) Math.atan2(2d * (rotation.w * rotation.x + rotation.y * rotation.z),
                        1 - 2d * (rotation.x * rotation.x + rotation.y * rotation.y)),
                (float) Math.asin(Math.max(-1, Math.min(1, 2d * (rotation.w * rotation.y - rotation.z * rotation.x)))),
                (float) Math.atan2(2d * (rotation.w * rotation.z + rotation.x * rotation.y),
                        1 - 2d * (rotation.y * rotation.y + rotation.z * rotation.z)));
    }

    private static Quaternionf parentRotation(GeoBone bone) {
        if (bone == null) return new Quaternionf();
        return parentRotation(bone.getParent()).rotateZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
    }

    private static Quaternionf parentRotation(GeoBone bone, Map<String, BoneSnapshot> snapshots) {
        if (bone == null) return new Quaternionf();
        return parentRotation(bone.getParent(), snapshots).mul(snapshotRotation(bone, snapshots));
    }

    private static Quaternionf snapshotRotation(GeoBone bone, Map<String, BoneSnapshot> snapshots) {
        BoneSnapshot snapshot = snapshots.get(bone.getName());
        if (snapshot == null) snapshot = bone.getInitialSnapshot();
        return snapshot == null ? new Quaternionf()
                : new Quaternionf().rotationZYX(snapshot.getRotZ(), snapshot.getRotY(), snapshot.getRotX());
    }

    @Override
    public void close() {
        bone.updateRotation(x, y, z);
        bone.resetStateChanges();
        if (rotationChanged) bone.markRotationAsChanged();
        if (positionChanged) bone.markPositionAsChanged();
        if (scaleChanged) bone.markScaleAsChanged();
    }
}
