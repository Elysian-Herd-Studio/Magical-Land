package top.elysianherd.magicaland.client.render;

final class PonyHeadLookMath {
    enum Pose { NORMAL, SWIMMING, FLYING, SLEEPING }
    record Rotation(float pitch, float yaw) {
        static final Rotation ZERO = new Rotation(0, 0);
    }

    private PonyHeadLookMath() {}

    static boolean shouldApply(boolean worldRender, boolean nested, String boneName) {
        return worldRender && !nested && "Head".equals(boneName);
    }

    static Rotation flight(org.joml.Quaternionf body, float x, float y, float z, float strength) {
        var local = body.conjugate(new org.joml.Quaternionf()).transform(new org.joml.Vector3f(x, y, z));
        if (!local.isFinite() || !Float.isFinite(strength)) return Rotation.ZERO;
        float yaw = (float) Math.atan2(local.x, local.z);
        float pitch = (float) Math.atan2(local.y, Math.hypot(local.x, local.z));
        return new Rotation(clamp(pitch * .4f, (float) Math.toRadians(-25), (float) Math.toRadians(25)) * strength,
                clamp(yaw * .5f, (float) Math.toRadians(-35), (float) Math.toRadians(35)) * strength);
    }

    static Rotation sample(float previousBodyYaw, float bodyYaw, float previousHeadYaw, float headYaw,
            float previousPitch, float pitch, float partialTick, Pose pose) {
        if (pose == Pose.SLEEPING || !finite(previousBodyYaw, bodyYaw, previousHeadYaw, headYaw,
                previousPitch, pitch, partialTick)) return Rotation.ZERO;
        float tick = clamp(partialTick, 0, 1);
        float body = lerpAngle(previousBodyYaw, bodyYaw, tick);
        float head = lerpAngle(previousHeadYaw, headYaw, tick);
        float yawDegrees = wrapDegrees(head - body);
        previousPitch = clamp(previousPitch, -90, 90);
        pitch = clamp(pitch, -90, 90);
        float pitchDegrees = previousPitch + (pitch - previousPitch) * tick;
        float yawLimit = 70, pitchLimit = 45;
        if (pose == Pose.SWIMMING) {
            yawDegrees *= .65f;
            pitchDegrees *= .5f;
            yawLimit = 40;
            pitchLimit = 25;
        } else if (pose == Pose.FLYING) {
            yawDegrees *= .5f;
            pitchDegrees *= .4f;
            yawLimit = 35;
            pitchLimit = 25;
        }
        // 与 GeoEntityRenderer 的 EntityModelData 一致，Gecko头骨骼使用相反的角度符号。
        return new Rotation((float) Math.toRadians(-clamp(pitchDegrees, -pitchLimit, pitchLimit)),
                (float) Math.toRadians(-clamp(yawDegrees, -yawLimit, yawLimit)));
    }

    private static float lerpAngle(float from, float to, float tick) {
        from = wrapDegrees(from);
        to = wrapDegrees(to);
        return from + wrapDegrees(to - from) * tick;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static boolean finite(float... values) {
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
}
