package top.elysianherd.magicaland.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TransformationBurstMath {
    public static final int LIFETIME_TICKS = 16;
    public static final int DOT_COUNT = 28;
    public static final int STAR_COUNT = 4;

    public record Speck(double x, double y, double z, double vx, double vy, double vz,
            float size, int rgb, boolean star, float angle) {}

    private TransformationBurstMath() {}

    public static List<Speck> sample(long seed, String bodyColor, String magicColor) {
        return sample(seed, bodyColor, magicColor, 0);
    }

    public static List<Speck> sample(long seed, String bodyColor, String magicColor, float bodyYaw) {
        Random random = new Random(seed);
        int body = parseColor(bodyColor, 0xFFFFFF);
        int magic = parseColor(magicColor, 0xAA00FF);
        double yaw = Float.isFinite(bodyYaw) ? Math.toRadians(bodyYaw % 360) : 0;
        double facingCos = Math.cos(yaw), facingSin = Math.sin(yaw);
        List<Speck> result = new ArrayList<>(DOT_COUNT + STAR_COUNT);
        for (int i = 0; i < DOT_COUNT + STAR_COUNT; i++) {
            boolean star = i >= DOT_COUNT;
            int kindIndex = star ? i - DOT_COUNT : i;
            int kindCount = star ? STAR_COUNT : DOT_COUNT;
            double angle = (kindIndex + random.nextDouble() * .7) * Math.PI * 2 / kindCount;
            double radius = Math.sqrt(.58 + random.nextDouble() * .42);
            double height = .12 + ((star ? kindIndex : i % 7) + random.nextDouble()) / (star ? STAR_COUNT : 7) * 1.30;
            double speed = .026 + random.nextDouble() * .016;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double vy = (height - .7) * .016 + .010;
            float size = star ? .038f + random.nextFloat() * .011f : .019f + random.nextFloat() * .0125f;
            float blend = star ? .68f : .28f + random.nextFloat() * .28f;
            int color = mixLuminous(body, magic, blend);
            double localX = cos * radius * .48, localZ = sin * radius * .65;
            double vx = cos * speed, vz = sin * speed;
            result.add(new Speck(localX * facingCos - localZ * facingSin, height,
                    localX * facingSin + localZ * facingCos,
                    vx * facingCos - vz * facingSin, vy, vx * facingSin + vz * facingCos,
                    size, color, star, random.nextFloat() * 6.2831855f));
        }
        return List.copyOf(result);
    }

    public static float opacity(float age) {
        float ticks = Math.max(0, Math.min(LIFETIME_TICKS, age));
        float fadeIn = Math.min(1, ticks / 1.5f);
        float fadeOut = Math.min(1, (LIFETIME_TICKS - ticks) / (LIFETIME_TICKS - 4f));
        return .88f * fadeIn * fadeOut;
    }

    public static double displacement(float age) {
        double ticks = Math.max(0, Math.min(LIFETIME_TICKS, age));
        return (1 - Math.pow(.9, ticks)) / .1;
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) return fallback;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6 && hex.length() != 8) return fallback;
        try { return (int) Long.parseLong(hex, 16) & 0xFFFFFF; }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int mixLuminous(int body, int magic, float blend) {
        int result = 0;
        for (int shift : new int[] {16, 8, 0}) {
            float channel = ((body >> shift) & 255) * (1 - blend) + ((magic >> shift) & 255) * blend;
            int luminous = Math.round(64 + channel * .74f);
            result |= Math.max(64, Math.min(250, luminous)) << shift;
        }
        return result;
    }
}
