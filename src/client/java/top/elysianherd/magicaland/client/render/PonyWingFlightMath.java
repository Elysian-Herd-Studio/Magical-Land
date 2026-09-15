package top.elysianherd.magicaland.client.render;

import org.joml.Quaternionf;
import top.elysianherd.magicaland.api.client.FlightPose;

public final class PonyWingFlightMath {
    private PonyWingFlightMath() {}

    public static Quaternionf rotation(FlightPose pose) {
        Quaternionf rotation = pose.rotation();
        if (pose.mode() == FlightPose.Mode.REBOUND)
            rotation.rotateX((float) (-Math.PI * 6 * smooth(pose.reboundProgress() / .84f)));
        return rotation;
    }

    public static float curl(FlightPose pose) {
        return pose.mode() == FlightPose.Mode.REBOUND
                ? smooth(pose.reboundProgress() / .12f) * (1 - smooth((pose.reboundProgress() - .78f) / .22f)) : 0;
    }

    static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
