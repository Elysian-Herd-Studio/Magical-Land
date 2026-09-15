package top.elysianherd.magicaland.client.render;

/** 从不透明区域的色系面积取色，同色阴影和高光不拆成装饰色。 */
public final class ArmorPalette {
    private static final int MAX_SAMPLES = 16_384;
    private static final int RGB_BINS = 32 * 32 * 32;
    private static final int HUES = 36, NEUTRAL = HUES, FAMILY_RADIUS = 2;
    private static final int ACCENT_HUE_GAP = 4;
    private static final int MIN_ACCENT_PERCENT = 3;

    public record Palette(int body, int accent) { }

    private ArmorPalette() { }

    public static Palette extract(int[] abgr) {
        if (abgr == null || abgr.length == 0) return null;
        int[] weights = new int[RGB_BINS], reds = new int[RGB_BINS];
        int[] greens = new int[RGB_BINS], blues = new int[RGB_BINS];
        int count = Math.min(abgr.length, MAX_SAMPLES), total = 0;
        for (int i = 0; i < count; i++) {
            int pixel = abgr[sampleIndex(i, count, abgr.length)];
            int alpha = pixel >>> 24;
            if (alpha == 0) continue;
            int red = pixel & 255, green = pixel >>> 8 & 255, blue = pixel >>> 16 & 255;
            int bin = (red >>> 3) << 10 | (green >>> 3) << 5 | blue >>> 3;
            weights[bin] += alpha;
            reds[bin] += red * alpha;
            greens[bin] += green * alpha;
            blues[bin] += blue * alpha;
            total += alpha;
        }
        if (total == 0) return null;

        byte[] families = new byte[RGB_BINS];
        int[] areas = new int[HUES + 1];
        for (int bin = 0; bin < RGB_BINS; bin++) {
            if (weights[bin] == 0) continue;
            int family = family(average(reds[bin], weights[bin]), average(greens[bin], weights[bin]),
                    average(blues[bin], weights[bin]));
            families[bin] = (byte) family;
            areas[family] += weights[bin];
        }
        int primary = strongest(areas);
        if (areas[NEUTRAL] >= area(areas, primary)) primary = NEUTRAL;
        int body = representative(primary, weights, reds, greens, blues, families, areas);
        int[] accents = areas.clone();
        accents[NEUTRAL] = 0;
        if (primary != NEUTRAL) for (int hue = 0; hue < HUES; hue++)
            if (distance(hue, primary) < ACCENT_HUE_GAP) accents[hue] = 0;
        int secondary = strongest(accents);
        int secondaryArea = secondary < 0 ? 0 : area(accents, secondary);
        // 几个高饱和噪点不抢装饰色；透明边缘按 alpha 计面积。
        if (secondaryArea < 3 * 255 || (long) secondaryArea * 100 < (long) total * MIN_ACCENT_PERCENT)
            return new Palette(body, body);
        int accent = representative(secondary, weights, reds, greens, blues, families, accents);
        return new Palette(body, accent);
    }

    private static int sampleIndex(int index, int count, int length) {
        if (count == length) return index;
        long start = (long) index * length / count, end = (long) (index + 1) * length / count;
        int hash = index + 0x9E3779B9;
        hash = (hash ^ hash >>> 16) * 0x7FEB352D;
        hash = (hash ^ hash >>> 15) * 0x846CA68B;
        hash ^= hash >>> 16;
        // 每段只读一个确定的位置，避免固定步长碰巧漏掉条纹。
        return (int) (start + Integer.toUnsignedLong(hash) % (end - start));
    }

    private static int family(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue)), min = Math.min(red, Math.min(green, blue));
        int chroma = max - min;
        if (chroma < 12 || chroma < max * 0.16) return NEUTRAL;
        double hue = max == red ? (green - blue) / (double) chroma
                : max == green ? (blue - red) / (double) chroma + 2
                : (red - green) / (double) chroma + 4;
        return (int) Math.floor(((hue * 60 + 360) % 360 + 5) / 10) % HUES;
    }

    private static int strongest(int[] areas) {
        int best = -1, bestArea = 0, bestCenter = -1;
        for (int candidate = 0; candidate < HUES; candidate++) {
            if (areas[candidate] == 0) continue;
            int area = area(areas, candidate);
            if (area > bestArea || area == bestArea && areas[candidate] > bestCenter) {
                best = candidate;
                bestArea = area;
                bestCenter = areas[candidate];
            }
        }
        return bestArea == 0 ? -1 : best;
    }

    private static int area(int[] areas, int center) {
        if (center < 0) return 0;
        if (center == NEUTRAL) return areas[NEUTRAL];
        int total = 0;
        for (int offset = -FAMILY_RADIUS; offset <= FAMILY_RADIUS; offset++)
            total += areas[(center + offset + HUES) % HUES];
        return total;
    }

    private static int representative(int center, int[] weights, int[] reds, int[] greens, int[] blues,
                                      byte[] families, int[] eligible) {
        int[] tones = new int[256];
        int total = 0;
        for (int bin = 0; bin < RGB_BINS; bin++) {
            if (weights[bin] == 0 || eligible[families[bin]] == 0 || !belongs(families[bin], center)) continue;
            int value = tone(average(reds[bin], weights[bin]), average(greens[bin], weights[bin]),
                    average(blues[bin], weights[bin]));
            tones[value] += weights[bin];
            total += weights[bin];
        }
        int targetTone = 0, accumulated = 0;
        for (; targetTone < 255; targetTone++) {
            accumulated += tones[targetTone];
            if ((long) accumulated * 100 >= (long) total * 65) break;
        }
        int bestWeight = -1, bestDistance = Integer.MAX_VALUE, result = 0;
        for (int bin = 0; bin < RGB_BINS; bin++) {
            if (weights[bin] == 0 || eligible[families[bin]] == 0 || !belongs(families[bin], center)) continue;
            int red = average(reds[bin], weights[bin]), green = average(greens[bin], weights[bin]);
            int blue = average(blues[bin], weights[bin]), distance = Math.abs(tone(red, green, blue) - targetTone);
            // 先限定本色亮度附近，再取常见色，避免大面积暗边被二次压暗。
            if (distance > 3) continue;
            if (weights[bin] > bestWeight || weights[bin] == bestWeight && distance < bestDistance) {
                bestWeight = weights[bin];
                bestDistance = distance;
                result = red << 16 | green << 8 | blue;
            }
        }
        return result;
    }

    private static boolean belongs(int family, int center) {
        return center == NEUTRAL ? family == NEUTRAL : family != NEUTRAL && distance(family, center) <= FAMILY_RADIUS;
    }

    private static int distance(int a, int b) {
        int distance = Math.abs(a - b);
        return Math.min(distance, HUES - distance);
    }

    private static int average(int weighted, int weight) { return (weighted + weight / 2) / weight; }
    private static int tone(int red, int green, int blue) { return (54 * red + 183 * green + 19 * blue + 128) >>> 8; }
}
