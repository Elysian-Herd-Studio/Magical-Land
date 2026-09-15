package top.elysianherd.magicaland.sound;

import com.google.gson.JsonObject;

public final class HoofStepProtocol {
    public static final int VERSION = 1;
    public static final String CAPABILITY = "hoof_step_version";
    public enum Delivery { NONE, ENTITY, POSITION }
    private HoofStepProtocol() {}

    public static boolean supported(JsonObject handshake) {
        if (handshake == null || !handshake.has(CAPABILITY)) return false;
        var version = handshake.get(CAPABILITY);
        return version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()
                && Integer.toString(VERSION).equals(version.getAsString());
    }
    public static Delivery delivery(boolean owner, boolean sameWorld, boolean audible, boolean tracked) {
        if (owner || !sameWorld || !audible) return Delivery.NONE;
        return tracked ? Delivery.ENTITY : Delivery.POSITION;
    }
    public static boolean mayIntercept(boolean supported, boolean playersCategory, boolean loadedPlayer, boolean blockStep) {
        return supported && playersCategory && loadedPlayer && blockStep;
    }
}
