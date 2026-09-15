package top.elysianherd.magicaland.client.gui.ponycustom;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PreviewCamera {
    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public float centerX() { return (minX + maxX) * .5f; }
        public float centerY() { return (minY + maxY) * .5f; }
        public float centerZ() { return (minZ + maxZ) * .5f; }
    }
    public record Pose(float x, float y, float z, float yaw, float pitch, float scale) {}
    private Pose pose;
    private float desiredYaw = 155, desiredPitch = -10;

    public void angle(float yaw, float pitch) {
        desiredYaw = wrap(yaw);
        desiredPitch = Math.max(-80, Math.min(80, pitch));
    }

    public void manual(float yaw, float pitch) {
        angle(yaw, pitch);
        if (pose != null) pose = new Pose(pose.x, pose.y, pose.z, desiredYaw, desiredPitch, pose.scale);
    }

    public Pose pose() { return pose; }
    public void reset() { pose = null; }

    public Pose update(Box box, int width, int height, double seconds, boolean snap) {
        float factor = (float) -Math.expm1(-Math.max(0, Math.min(.1, seconds)) * 12);
        if (pose == null || snap) factor = 1;
        float x = box.centerX(), y = box.centerY(), z = box.centerZ();
        float yaw = desiredYaw, pitch = desiredPitch;
        if (pose != null) {
            x = mix(pose.x, x, factor); y = mix(pose.y, y, factor); z = mix(pose.z, z, factor);
            yaw = pose.yaw + wrap(desiredYaw - pose.yaw) * factor;
            pitch = mix(pose.pitch, desiredPitch, factor);
        }
        float fit = fitScale(box, x, y, z, yaw, pitch, width, height);
        float scale = pose == null || snap ? fit : mix(pose.scale, fit, factor);
        pose = new Pose(x, y, z, wrap(yaw), pitch, scale);
        return pose;
    }

    public static float fitScale(Box box, float x, float y, float z, float yaw, float pitch, int width, int height) {
        Matrix4f rotation = new Matrix4f().rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch));
        float extentX = .01f, extentY = .01f;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ).sub(x, y, z);
            rotation.transformPosition(corner);
            extentX = Math.max(extentX, Math.abs(corner.x));
            extentY = Math.max(extentY, Math.abs(corner.y));
        }
        float availableX = Math.max(1, width - 12) * .43f;
        float availableY = Math.max(1, height - 12) * .43f;
        return Math.min(availableX / extentX, availableY / extentY);
    }

    public static float wrap(float angle) { return (angle % 360 + 540) % 360 - 180; }
    private static float mix(float a, float b, float factor) { return a + (b - a) * factor; }
}
