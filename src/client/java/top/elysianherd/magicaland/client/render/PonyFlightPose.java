package top.elysianherd.magicaland.client.render;

import software.bernie.geckolib.cache.object.GeoBone;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.animation.PonyFlightAnimations;

final class PonyFlightPose implements AutoCloseable {
    private final GeoBone bone;
    private final float rx, ry, rz, px, py, pz;
    private final boolean rotationChanged, positionChanged, scaleChanged;
    private boolean closed;

    static PonyFlightPose apply(GeoBone bone, PonyFlightVisuals.Frame frame, boolean world, boolean reRender) {
        if (!world || reRender || frame == null || !(frame.amount() > 0)) return null;
        boolean body = "Body".equals(bone.getName());
        boolean upperLeg = switch (bone.getName()) {
            case "LForeLeg", "RForeLeg", "LHindLeg", "RHindLeg" -> true;
            default -> false;
        };
        float curl = frame.curlDelta();
        var offset = PonyFlightAnimations.offset(bone.getName(), curl);
        if (!body && !upperLeg && (offset == null || curl == 0)) return null;
        float pitch = body ? frame.bodyPitch() : upperLeg ? frame.legPitch() : 0;
        if (PonyFlightAnimations.isFrontUpper(bone.getName())) pitch -= frame.frontLift();
        float roll = body ? frame.bodyRoll() : upperLeg ? frame.legRoll() : 0;
        float bob = body ? frame.bob() : 0;
        if (!Float.isFinite(pitch) || !Float.isFinite(roll) || !Float.isFinite(bob) || !Float.isFinite(curl)) return null;
        return new PonyFlightPose(bone, pitch, roll, bob, offset);
    }

    private PonyFlightPose(GeoBone bone, float pitch, float roll, float bob, PonyFlightAnimations.Limb offset) {
        this.bone = bone;
        rx = bone.getRotX(); ry = bone.getRotY(); rz = bone.getRotZ();
        px = bone.getPosX(); py = bone.getPosY(); pz = bone.getPosZ();
        rotationChanged = bone.hasRotationChanged();
        positionChanged = bone.hasPositionChanged();
        scaleChanged = bone.hasScaleChanged();
        bone.updateRotation(rx - (float) Math.toRadians(pitch), ry, rz + (float) Math.toRadians(roll));
        if (bob != 0) bone.updatePosition(px, py + bob, pz);
        if (offset != null) {
            bone.updateRotation(bone.getRotX() + offset.rx(), bone.getRotY() + offset.ry(), bone.getRotZ() + offset.rz());
            bone.updatePosition(px + offset.x(), py + offset.y(), pz + offset.z());
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        bone.updateRotation(rx, ry, rz);
        bone.updatePosition(px, py, pz);
        bone.resetStateChanges();
        if (rotationChanged) bone.markRotationAsChanged();
        if (positionChanged) bone.markPositionAsChanged();
        if (scaleChanged) bone.markScaleAsChanged();
    }
}
