package top.elysianherd.magicaland.client.animation;

public final class PonyJumpAnimation {
    private static final int APEX_HOLD_TICKS = 10;
    private int lastRisingTick = -1;

    public void update(int tick, double verticalVelocity, boolean airborne) {
        if (!airborne || tick < lastRisingTick) lastRisingTick = -1;
        if (airborne && verticalVelocity > 0) lastRisingTick = tick;
    }

    public boolean isJumping(int tick, double verticalVelocity) {
        // Keep short jumps intact, but release the held pose during a longer fall.
        return verticalVelocity > 0 || (lastRisingTick >= 0 && tick >= lastRisingTick
                && tick - lastRisingTick < APEX_HOLD_TICKS);
    }
}
