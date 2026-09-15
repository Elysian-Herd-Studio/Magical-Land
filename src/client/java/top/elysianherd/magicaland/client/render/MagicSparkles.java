package top.elysianherd.magicaland.client.render;

public final class MagicSparkles {
    private MagicSparkles() {}
    public record Spark(float x, float y, float z, float radius, float alpha) {}

    public static Spark sample(double ticks, int seed, int slot) {
        return sample(ticks, seed, slot, false);
    }

    public static Spark sampleFalling(double ticks, int seed, int slot) {
        return sample(ticks, seed, slot, true);
    }

    private static Spark sample(double ticks, int seed, int slot, boolean falling) {
        if (!Double.isFinite(ticks) || slot < 0 || slot >= 4) return null;
        int period = falling ? 24 : 60 + Math.floorMod(seed, 21);
        double clock = ticks + Math.floorMod(seed, period) - (falling ? slot * 6 : 0);
        long cycle = (long) Math.floor(clock / period);
        int count = falling ? 4 : 2 + (int) Math.floorMod(cycle + seed, 3);
        if (slot >= count) return null;
        double progress = falling
                ? (clock - cycle * period) / 18
                : (clock - cycle * period - 14 - slot * 2) / 18;
        if (progress <= 0 || progress >= 1) return null;
        double angle = slot * 2.39996 + Math.floorMod(seed, 100) * 0.06283 + Math.floorMod(cycle, 16) * 0.65;
        float spread = (float) (0.075 + 0.055 * progress);
        float alpha = (float) Math.sin(progress * Math.PI) * 0.65f;
        float radius = (0.014f + slot * 0.002f) * (float) (0.75 + progress * 0.7);
        float vertical = falling ? -(float) (progress * progress) * 0.14f : (float) progress * 0.14f;
        return new Spark((float) Math.cos(angle) * spread, vertical,
                (float) Math.sin(angle) * spread, radius, alpha);
    }
}
