package top.elysianherd.magicaland.client.render;

public final class UnicornFlightRimMath {
    public static final int SEGMENTS = 64, BANDS = 8;
    public static final float WIDTH = .08f, MAX_ALPHA = .32f;
    private UnicornFlightRimMath() {}

    @FunctionalInterface
    public interface VertexSink { void vertex(float x, float y, float red, float green, float blue, float alpha); }

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    public static float profile(float depth) {
        float t = clamp(depth), smooth = t * t * (3 - 2 * t);
        return (float) Math.pow(1 - smooth, 1.6);
    }

    public static void emit(int width, int height, float magic, int color, double ticks, VertexSink sink) {
        float amount = clamp(magic);
        if (width <= 0 || height <= 0 || amount <= 0 || !Double.isFinite(ticks) || sink == null) return;
        float red = .45f + .55f * ((color >>> 16) & 255) / 255f;
        float green = .45f + .55f * ((color >>> 8) & 255) / 255f;
        float blue = .45f + .55f * (color & 255) / 255f;
        for (int band = 0; band < BANDS; band++) for (int segment = 0; segment < SEGMENTS; segment++) {
            float outer = band / (float) BANDS, inner = (band + 1) / (float) BANDS;
            vertex(width, height, segment, outer, amount, red, green, blue, ticks, sink);
            vertex(width, height, segment, inner, amount, red, green, blue, ticks, sink);
            vertex(width, height, segment + 1, inner, amount, red, green, blue, ticks, sink);
            vertex(width, height, segment + 1, outer, amount, red, green, blue, ticks, sink);
        }
    }

    private static void vertex(int width, int height, int segment, float depth, float amount,
                               float red, float green, float blue, double ticks, VertexSink sink) {
        double perimeter = (segment % SEGMENTS) * Math.PI * 2 / SEGMENTS;
        double flow = Math.sin(perimeter * 2 - Math.IEEEremainder(ticks * .045, Math.PI * 2));
        double ripple = Math.sin(perimeter * 5 + Math.IEEEremainder(ticks * .017, Math.PI * 2));
        float inset = Math.min(width, height) * WIDTH * depth * (float) (1 + .06 * flow + .025 * ripple);
        int side = (segment % SEGMENTS) / 16;
        float t = (segment % 16) / 16f;
        float x = switch (side) {
            case 0 -> inset + (width - 2 * inset) * t;
            case 1 -> width - inset;
            case 2 -> width - inset - (width - 2 * inset) * t;
            default -> inset;
        };
        float y = switch (side) {
            case 0 -> inset;
            case 1 -> inset + (height - 2 * inset) * t;
            case 2 -> height - inset;
            default -> height - inset - (height - 2 * inset) * t;
        };
        float alpha = MAX_ALPHA * amount * profile(depth) * (float) (.9 + .07 * flow + .03 * ripple);
        sink.vertex(x, y, red, green, blue, alpha);
    }

    public static final class Entrance {
        private double previous = Double.NaN, started;
        public void reset() { previous = Double.NaN; }
        public float sample(double ticks, float magic) {
            if (!Double.isFinite(ticks) || clamp(magic) <= 0) { reset(); return 0; }
            if (!Double.isFinite(previous) || ticks < previous || ticks - previous > 4) started = ticks;
            previous = ticks;
            float t = clamp((float) ((ticks - started) / 4));
            return clamp(magic) * t * t * (3 - 2 * t);
        }
    }
}
