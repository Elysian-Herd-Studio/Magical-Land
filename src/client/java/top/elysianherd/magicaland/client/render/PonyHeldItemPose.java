package top.elysianherd.magicaland.client.render;

import software.bernie.geckolib.cache.object.GeoBone;
import top.elysianherd.magicaland.client.animation.PonyFlightAnimations;

final class PonyHeldItemPose implements AutoCloseable {
    private final GeoBone bone;
    private final float rx, ry, rz, px, py, pz;
    private final boolean rotationChanged, positionChanged, scaleChanged;

    static PonyHeldItemPose apply(GeoBone bone, PonyHeldItems.Frame frame, Weights weights, boolean reRender) {
        if (reRender || frame.player() == null) return null;
        String name = bone.getName();
        if (name.equals("Head")) {
            var trident = weights.trident;
            boolean throwing = trident.windup() > 0 || trident.release() > 0 || trident.settle() > 0;
            float progress = frame.mouthSwing();
            boolean swinging = !throwing && progress > 0 && progress < 1;
            float cuePitch = throwing ? 0 : weights.cue.headPitch();
            if (!throwing && !swinging && cuePitch == 0) return null;
            var saved = new PonyHeldItemPose(bone);
            float pitch = cuePitch + trident.headPitchRadians()
                    + (swinging ? (float) Math.sin(progress * Math.PI) * (float) Math.toRadians(3) : 0);
            float yaw = swinging ? (float) Math.sin(progress * Math.PI * 2) * (float) Math.toRadians(6) : 0;
            bone.updateRotation(saved.rx + pitch,
                    saved.ry + (frame.mainLeft() ? -yaw : yaw) + trident.headYawRadians(), saved.rz);
            return saved;
        }
        if (!PonyFlightAnimations.isFrontLeg(name)) return null;
        boolean left = name.startsWith("L");
        float weight = left ? weights.left : weights.right;
        float cue = PonyFlightAnimations.isFrontUpper(name) ? (left ? weights.cue.leftPitch() : weights.cue.rightPitch()) : 0;
        if (weight <= .001f) {
            if (cue == 0) return null;
            var saved = new PonyHeldItemPose(bone);
            bone.updateRotation(saved.rx - cue, saved.ry, saved.rz);
            return saved;
        }
        var source = carryingPose(name, weights.usePitch(name.startsWith("L")));
        if (source == null) return null;
        var saved = new PonyHeldItemPose(bone);
        bone.updateRotation(lerp(saved.rx, source.rx(), weight) - cue, lerp(saved.ry, source.ry(), weight), lerp(saved.rz, source.rz(), weight));
        bone.updatePosition(lerp(saved.px, source.x(), weight), lerp(saved.py, source.y(), weight), lerp(saved.pz, source.z(), weight));
        return saved;
    }

    static PonyFlightAnimations.Limb carryingPose(String name) {
        return carryingPose(name, 0);
    }

    static PonyFlightAnimations.Limb carryingPose(String name, float usePitch) {
        var source = PonyFlightAnimations.limb(name);
        if (source == null || !PonyFlightAnimations.isFrontLeg(name)) return null;
        float factor = .95f;
        float rx = source.rx() * factor, ry = source.ry() * factor, rz = source.rz() * factor;
        if (PonyFlightAnimations.isFrontUpper(name)) {
            String side = name.substring(0, 1);
            var calf = PonyFlightAnimations.limb(side + "FrontCalf");
            var hoof = PonyFlightAnimations.limb(side + "FrontHoof");
            if (calf == null || hoof == null) return null;
            // 整条蜷腿转向前方、蹄底朝上，子关节角度及补偿保持成套。
            rx = -(float) Math.PI - (calf.rx() + hoof.rx()) * factor;
            // 只绕肩部抬动整条前腿，保留小腿与蹄子的角度、接缝补偿。
            rx -= Math.max(0, Math.min((float) Math.toRadians(32), usePitch));
            ry = name.startsWith("L") ? (float) Math.PI : -(float) Math.PI; rz = 0;
            float inward = (float) Math.toRadians(8) * Math.max(0, Math.min(1, usePitch / (float) Math.toRadians(28)));
            ry += name.startsWith("L") ? -inward : inward;
        }
        return new PonyFlightAnimations.Limb(rx, ry, rz, source.x() * factor, source.y() * factor, source.z() * factor);
    }

    private PonyHeldItemPose(GeoBone bone) {
        this.bone = bone;
        rx = bone.getRotX(); ry = bone.getRotY(); rz = bone.getRotZ();
        px = bone.getPosX(); py = bone.getPosY(); pz = bone.getPosZ();
        rotationChanged = bone.hasRotationChanged(); positionChanged = bone.hasPositionChanged(); scaleChanged = bone.hasScaleChanged();
    }
    @Override public void close() {
        bone.updateRotation(rx, ry, rz); bone.updatePosition(px, py, pz); bone.resetStateChanges();
        if (rotationChanged) bone.markRotationAsChanged();
        if (positionChanged) bone.markPositionAsChanged();
        if (scaleChanged) bone.markScaleAsChanged();
    }
    private static float lerp(float from, float to, float weight) { return from + (to - from) * weight; }

    static final class Weights {
        float left, right;
        private float leftUse, rightUse;
        private final PonyCarryCue cues = new PonyCarryCue();
        private PonyCarryCue.Pose cue = PonyCarryCue.Pose.NONE;
        private PonyTridentMotion.Pose trident = PonyTridentMotion.Pose.NONE;
        private double previous = Double.NaN;
        void update(double tick, PonyHeldItems.Frame frame) {
            update(tick, frame.raises(true), frame.raises(false), frame.consumingPitch(true), frame.consumingPitch(false));
            trident = PonyTridentVisuals.sample(frame.player(), tick);
            Object main = frame.main().isEmpty() ? null : frame.main().getItem();
            Object off = frame.off().isEmpty() ? null : frame.off().getItem();
            cue = cues.sample(tick, frame.raises(true) ? (frame.mainLeft() ? main : off) : null,
                    frame.raises(false) ? (frame.mainLeft() ? off : main) : null,
                    frame.mainGrip() == PonyHeldItems.Grip.MOUTH ? main : frame.offGrip() == PonyHeldItems.Grip.MOUTH ? off : null);
        }
        void update(double tick, boolean raiseLeft, boolean raiseRight) {
            update(tick, raiseLeft, raiseRight, 0, 0);
        }
        private void update(double tick, boolean raiseLeft, boolean raiseRight, float useLeft, float useRight) {
            if (!Double.isFinite(tick)) { reset(); return; }
            if (!Double.isFinite(previous) || tick < previous || tick - previous > 20) {
                leftUse = useLeft; rightUse = useRight;
            } else {
                float angleStep = (float) (Math.max(0, tick - previous) * Math.toRadians(10));
                leftUse += Math.max(-angleStep, Math.min(angleStep, useLeft - leftUse));
                rightUse += Math.max(-angleStep, Math.min(angleStep, useRight - rightUse));
            }
            left = raiseLeft ? 1 : 0; right = raiseRight ? 1 : 0;
            if (!raiseLeft) leftUse = 0;
            if (!raiseRight) rightUse = 0;
            previous = tick;
        }
        float usePitch(boolean leftArm) { return leftArm ? leftUse : rightUse; }
        void reset() {
            previous = Double.NaN;
            left = right = leftUse = rightUse = 0;
            cues.reset(); cue = PonyCarryCue.Pose.NONE;
            trident = PonyTridentMotion.Pose.NONE;
        }
    }
}
