package top.elysianherd.magicaland.client.emote;

final class EmoteWheelLayout {
    static final int ENTRIES = 2;
    static final int STOP = ENTRIES;
    static final double SECTOR = Math.PI * 2 / ENTRIES;
    static final double FIRST_ANGLE = Math.PI;

    private EmoteWheelLayout() { }

    static double radius(int width, int height) {
        return Math.max(24, Math.min(106, Math.min((width - 24) / 2.0, (height - 44) / 2.0)));
    }

    static int entryAt(int width, int height, double mouseX, double mouseY) {
        double x = mouseX - width / 2.0;
        double y = mouseY - height / 2.0;
        double distance = Math.hypot(x, y);
        double radius = radius(width, height);
        if (!Double.isFinite(distance) || distance > radius + 4) return -1;
        if (distance < radius * .31) return STOP;
        if (distance < radius * .34) return -1;
        return Math.floorMod((int) Math.floor((Math.atan2(y, x) - FIRST_ANGLE + SECTOR / 2) / SECTOR), ENTRIES);
    }
}
