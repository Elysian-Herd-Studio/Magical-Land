package top.elysianherd.magicaland.client.emote;

import java.util.ArrayList;
import java.util.List;

record EmoteWheelPixels(int step, List<Span> spans) {
    static EmoteWheelPixels create(int width, int height) {
        double radius = EmoteWheelLayout.radius(width, height), cx = width / 2.0, cy = height / 2.0;
        int step = radius >= 60 ? 2 : 1;
        int left = (int) Math.floor(cx - radius), top = (int) Math.floor(cy - radius);
        int columns = (int) Math.ceil((cx + radius - left) / step);
        int rows = (int) Math.ceil((cy + radius - top) / step);
        int[][] slots = new int[rows][columns];
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            double px = left + (x + .5) * step, py = top + (y + .5) * step;
            slots[y][x] = Math.hypot(px - cx, py - cy) > radius ? -1
                    : EmoteWheelLayout.entryAt(width, height, px, py);
        }
        var spans = new ArrayList<Span>();
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns;) {
            int slot = slots[y][x];
            if (slot < 0) { x++; continue; }
            int shade = shade(slots, x, y, slot), end = x + 1;
            while (end < columns && slots[y][end] == slot && shade(slots, end, y, slot) == shade) end++;
            spans.add(new Span(left + x * step, top + y * step, (end - x) * step, step, slot, shade));
            x = end;
        }
        return new EmoteWheelPixels(step, List.copyOf(spans));
    }

    private static int shade(int[][] slots, int x, int y, int slot) {
        if (at(slots, x - 1, y) != slot || at(slots, x + 1, y) != slot
                || at(slots, x, y - 1) != slot || at(slots, x, y + 1) != slot) return 0;
        if (at(slots, x - 2, y) != slot || at(slots, x, y - 2) != slot) return 1;
        if (at(slots, x + 2, y) != slot || at(slots, x, y + 2) != slot) return 2;
        return 3;
    }

    private static int at(int[][] slots, int x, int y) {
        return y < 0 || y >= slots.length || x < 0 || x >= slots[0].length ? -1 : slots[y][x];
    }

    static int color(int shade, boolean hovered, boolean selected) {
        return switch (shade) {
            case 0 -> selected ? 0xFFF0F0F0 : 0xFF191919;
            case 1 -> hovered ? 0xFFD2D2D2 : 0xFFB0B0B0;
            case 2 -> hovered ? 0xFF555555 : 0xFF393939;
            default -> hovered ? 0xFF8B8B8B : 0xFF686868;
        };
    }

    record Span(int x, int y, int width, int height, int slot, int shade) {}
}
