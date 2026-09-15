package top.elysianherd.magicaland.client.render;

import software.bernie.geckolib.cache.object.GeoBone;
import top.elysianherd.magicaland.api.client.FlightPose;
import top.elysianherd.magicaland.client.animation.PonyWingFlightAnimations;

final class PonyWingFlightPose implements AutoCloseable {
    private final GeoBone bone;
    private final float rx, ry, rz, x, y, z, sx, sy, sz;
    private final boolean rotated, positioned, scaled;

    private PonyWingFlightPose(GeoBone bone) {
        this.bone = bone;
        rx = bone.getRotX(); ry = bone.getRotY(); rz = bone.getRotZ();
        x = bone.getPosX(); y = bone.getPosY(); z = bone.getPosZ();
        sx = bone.getScaleX(); sy = bone.getScaleY(); sz = bone.getScaleZ();
        rotated = bone.hasRotationChanged(); positioned = bone.hasPositionChanged(); scaled = bone.hasScaleChanged();
    }

    static PonyWingFlightPose apply(GeoBone bone, FlightPose pose, double ticks) {
        if (pose == null) return null;
        String name = bone.getName();
        boolean root = "Root".equals(name), body = "Body".equals(name), opened = name.contains("OpendWing");
        boolean closed = "LClosedWing".equals(name) || "RClosedWing".equals(name);
        float curl = PonyWingFlightMath.curl(pose);
        var tucked = curl > 0 ? PonyWingFlightAnimations.curled(name, pose.reboundProgress()) : null;
        if (!root && !body && !opened && !closed && tucked == null) return null;
        var saved = new PonyWingFlightPose(bone);
        if (root) { bone.updateRotation(0, 0, 0); bone.updateScale(1, 1, 1); }
        if (body) {
            var hover = switch (pose.mode()) {
                case NORMAL, BRAKE, LANDING -> PonyWingFlightAnimations.hoveringBody(ticks);
                default -> null;
            };
            bone.updateScale(hover == null ? 1 : hover.sx(), hover == null ? 1 : hover.sy(), hover == null ? 1 : hover.sz());
            bone.updatePosition(bone.getPosX(), hover == null ? 0 : hover.y(), bone.getPosZ());
            if (pose.mode() == FlightPose.Mode.GLIDE || pose.mode() == FlightPose.Mode.BOOST) {
                bone.updateRotation(0, 0, 0);
            }
        }
        if (opened) {
            var flap = PonyWingFlightAnimations.wing(name, ticks);
            if (flap != null) {
                float strength = pose.flapStrength();
                if (pose.mode() == FlightPose.Mode.BRAKE || pose.mode() == FlightPose.Mode.LANDING) strength = Math.max(1.35f, strength);
                boolean tip = name.endsWith("01");
                float direction = name.startsWith("L") ? 1 : -1;
                float neutral = direction * (tip ? -.12f : .08f);
                bone.updateRotation(flap.rx() * Math.min(1, strength), flap.ry() * Math.min(1, strength),
                        neutral + (flap.rz() - neutral) * Math.min(1.2f, strength));
                bone.updatePosition(flap.x(), flap.y() * Math.min(1, strength), flap.z());
            }
        }
        if (tucked != null && !root) {
            var initial = bone.getInitialSnapshot();
            float bx = initial == null ? 0 : initial.getRotX(), by = initial == null ? 0 : initial.getRotY(), bz = initial == null ? 0 : initial.getRotZ();
            bone.updateRotation(mix(bone.getRotX(), bx + tucked.rx(), curl), mix(bone.getRotY(), by + tucked.ry(), curl),
                    mix(bone.getRotZ(), bz + tucked.rz(), curl));
            bone.updatePosition(mix(bone.getPosX(), tucked.x(), curl), mix(bone.getPosY(), tucked.y(), curl), mix(bone.getPosZ(), tucked.z(), curl));
            // Zero scales select folded wings; other authored squash/stretch is removed for this move.
            bone.updateScale(mix(bone.getScaleX(), tucked.sx() == 0 ? 0 : 1, curl),
                    mix(bone.getScaleY(), tucked.sy() == 0 ? 0 : 1, curl), mix(bone.getScaleZ(), tucked.sz() == 0 ? 0 : 1, curl));
        }
        if (closed) bone.updateScale(curl, curl, curl);
        return saved;
    }

    private static float mix(float from, float to, float amount) { return from + (to - from) * amount; }

    @Override public void close() {
        bone.updateRotation(rx, ry, rz); bone.updatePosition(x, y, z); bone.updateScale(sx, sy, sz);
        bone.resetStateChanges();
        if (rotated) bone.markRotationAsChanged();
        if (positioned) bone.markPositionAsChanged();
        if (scaled) bone.markScaleAsChanged();
    }
}
