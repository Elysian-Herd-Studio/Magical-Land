package top.elysianherd.magicaland.client.animation;

public final class PonyLandingAnimation {
    public enum Landing { NONE, NORMAL, HEAVY }

    private float maxFallDistance;
    private float fallDistanceOffset;
    private boolean flewSinceGround;
    private boolean wasOnGround = true;
    private int landStartTime;
    private int lastTick = Integer.MIN_VALUE;
    private Landing landing = Landing.NONE;

    public Landing update(int tick, float fallDistance, boolean onGround, boolean flying,
                          boolean touchingWater, boolean interrupted) {
        if (tick < lastTick) reset();
        lastTick = tick;
        float distance = Float.isFinite(fallDistance) ? Math.max(0, fallDistance) : 0;
        if (touchingWater) {
            clearFall();
            landing = Landing.NONE;
        } else if (!onGround) {
            if (flying) {
                maxFallDistance = 0;
                // 托举不改原版 fallDistance，动画只计算托举后新增的下坠。
                fallDistanceOffset = distance;
                flewSinceGround = true;
            } else {
                if (distance < fallDistanceOffset) fallDistanceOffset = 0;
                maxFallDistance = Math.max(maxFallDistance, distance - fallDistanceOffset);
            }
        } else {
            if (!wasOnGround && (maxFallDistance > .5f || flewSinceGround)) {
                landing = maxFallDistance >= 3 ? Landing.HEAVY : Landing.NORMAL;
                landStartTime = tick;
            }
            clearFall();
        }
        wasOnGround = onGround;
        if (interrupted || flying || touchingWater || !onGround
                || (long) tick - landStartTime >= (landing == Landing.HEAVY ? 20 : 10)) {
            landing = Landing.NONE;
        }
        return landing;
    }

    public boolean isLanding() { return landing != Landing.NONE; }

    private void clearFall() {
        maxFallDistance = 0;
        fallDistanceOffset = 0;
        flewSinceGround = false;
    }

    private void reset() {
        clearFall();
        wasOnGround = true;
        landing = Landing.NONE;
    }
}
