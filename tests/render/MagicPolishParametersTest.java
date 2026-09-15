package top.elysianherd.magicaland.client.render;

import java.util.Arrays;
import org.joml.Vector3f;

public final class MagicPolishParametersTest {
    private static int checks;

    public static void main(String[] args) {
        shellThickness();
        sparkSchedule();
        System.out.println("PASS magic polish parameters: " + checks + " shell displacement, unchanged opacity, star cadence/lifetime/count/size checks");
    }

    private static void shellThickness() {
        check(Arrays.equals(ItemAuraGeometry.OPACITY, new float[] {.12f, .095f, .072f, .050f, .030f, .016f}),
                "six shell layers and original opacities remain exact");
        for (Vector3f center : new Vector3f[] {new Vector3f(), new Vector3f(2, -1, .75f)})
            for (float extent : new float[] {.01f, .02f, .25f, 1, 4})
                for (float direction : new float[] {-1, 0, 1})
                    for (Vector3f normal : new Vector3f[] {new Vector3f(), new Vector3f(0, 0, 1),
                            new Vector3f(0, 0, -1), new Vector3f(1, 2, -1).normalize()})
                        for (int layer = 0; layer < 6; layer++) {
                            Vector3f position = new Vector3f(center).add(direction * extent * .5f, extent * .3f, -extent * .1f);
                            var vertex = new ItemAuraGeometry.Vertex(position.x, position.y, position.z, .32f, .74f,
                                    normal.x, normal.y, normal.z, .4f);
                            float extraDilation = .025f + layer * .024f;
                            float push = extent * (.007f + layer * .004f);
                            Vector3f oldOffset = new Vector3f(position).sub(center).mul(extraDilation).fma(push, normal);
                            Vector3f expected = new Vector3f(position).fma(1.65f, oldOffset);
                            Vector3f actual = ItemAuraGeometry.expanded(vertex, center, extent, layer);
                            check(actual.isFinite() && actual.distance(expected) < .00001f,
                                    "multiply only surface offset by 1.65, not original item or translation");
                            check(vertex.x() == position.x && vertex.y() == position.y && vertex.z() == position.z
                                    && vertex.u() == .32f && vertex.v() == .74f && vertex.alpha() == .4f,
                                    "original vertex, alpha and UV remain unchanged");
                            var centered = new ItemAuraGeometry.Vertex(center.x, center.y, center.z, 0, 0,
                                    normal.x, normal.y, normal.z, 1);
                            check(ItemAuraGeometry.expanded(centered, center, extent, layer)
                                    .distance(new Vector3f(center).fma(push * 1.65f, normal)) < .00001f,
                                    "normal push alone increases by 1.65");
                        }
    }

    private static void sparkSchedule() {
        for (int index = -1; index <= 211; index++) {
            int seed = index == -1 ? Integer.MIN_VALUE : index == 211 ? Integer.MAX_VALUE : index;
            int period = 24;
            int phase = Math.floorMod(seed, period);
            for (long cycle = -3; cycle < 9; cycle++) {
                double start = cycle * period - phase;
                check(MagicSparkles.sampleFalling(start, seed, 0) == null && MagicSparkles.sampleFalling(start + .001, seed, 0) != null,
                        "first falling star begins just after its cycle offset");
                check(MagicSparkles.sampleFalling(start + 18, seed, 0) == null, "first star lifetime is exactly 18 ticks");
                check(MagicSparkles.sampleFalling(start + period, seed, 0) == null
                                && MagicSparkles.sampleFalling(start + period + .001, seed, 0) != null,
                        "next first-star onset follows the exact seeded period");
                for (int slot = 0; slot < 4; slot++) {
                    double birth = start + slot * 6;
                    check(MagicSparkles.sampleFalling(birth, seed, slot) == null
                                    && MagicSparkles.sampleFalling(birth + 18, seed, slot) == null,
                            "exclusive birth/death endpoints unchanged");
                    for (double progress : new double[] {.0001, .1, .5, .9, .9999}) {
                        var star = MagicSparkles.sampleFalling(birth + progress * 18, seed, slot);
                        check(star != null, "all selected slots live for the full 18-tick interval");
                        float radius = (.014f + slot * .002f) * (float) (.75 + progress * .7);
                        float alpha = (float) Math.sin(progress * Math.PI) * .65f;
                        check(near(star.radius(), radius) && near(star.alpha(), alpha), "radius and fade curves unchanged");
                        check(near(star.y(), (float) (-progress * progress * .14))
                                        && Math.abs(Math.hypot(star.x(), star.z()) - (.075 + .055 * progress)) < .00001,
                                "accelerating falling motion and spread remain unchanged");
                        check(star.radius() > 0 && star.radius() < .03f && star.alpha() > 0 && star.alpha() <= .65f,
                                "stars remain tiny and softly faded");
                    }
                }
                int activeFrames = 0;
                for (int frame = 0; frame < period * 4; frame++) {
                    double ticks = cycle * period - phase + frame * .25;
                    int active = 0;
                    for (int slot = 0; slot < 4; slot++) if (MagicSparkles.sampleFalling(ticks, seed, slot) != null) active++;
                    check(active >= 2 && active <= 4, "continuous refresh keeps at least two stars active");
                    activeFrames++;
                }
                check(activeFrames == period * 4, "falling stars refresh continuously");
            }
        }
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(MagicSparkles.sampleFalling(invalid, 0, 0) == null, "invalid time remains safe");
        check(MagicSparkles.sampleFalling(20, 0, -1) == null && MagicSparkles.sampleFalling(20, 0, 4) == null, "invalid slots remain safe");
    }

    private static boolean near(float first, float second) { return Math.abs(first - second) < .00001f; }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
