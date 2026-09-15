package top.elysianherd.magicaland.api.client;

import org.joml.Quaternionf;

/** Unit rotation from local +Z forward / +Y up to world axes. Client visual contract, API 1.6. */
public record FlightPose(float x, float y, float z, float w, Mode mode, float flapStrength, float reboundProgress) {
    public enum Mode { NORMAL, GLIDE, BRAKE, BOOST, REBOUND, LANDING }

    public FlightPose {
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
        if (!Double.isFinite(length) || length < .000001 || mode == null
                || !Float.isFinite(flapStrength) || !Float.isFinite(reboundProgress))
            throw new IllegalArgumentException("Flight pose must be finite and have a nonzero rotation");
        x /= length; y /= length; z /= length; w /= length;
        flapStrength = Math.max(0, Math.min(2, flapStrength));
        reboundProgress = Math.max(0, Math.min(1, reboundProgress));
    }

    /** Returns a fresh quaternion; callers cannot mutate the registered pose. */
    public Quaternionf rotation() { return new Quaternionf(x, y, z, w); }
}
