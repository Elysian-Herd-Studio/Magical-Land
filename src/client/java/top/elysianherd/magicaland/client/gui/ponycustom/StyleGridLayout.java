package top.elysianherd.magicaland.client.gui.ponycustom;

public record StyleGridLayout(int width, int count, int columns, int cardWidth, int rows) {
    public static final int CARD_HEIGHT = 68;
    public static final int GAP = 4;
    public static final int ROW_HEIGHT = CARD_HEIGHT + GAP;

    public static StyleGridLayout of(int width, int count) {
        width = Math.max(1, width);
        count = Math.max(0, count);
        int columns = Math.max(1, Math.min(4, (width + GAP) / 66));
        int cardWidth = Math.max(1, (width - (columns - 1) * GAP) / columns);
        return new StyleGridLayout(width, count, columns, cardWidth, (count + columns - 1) / columns);
    }

    public int height() { return Math.max(20, rows * ROW_HEIGHT - GAP); }
    public int x(int index) { return index % columns * (cardWidth + GAP); }
    public int y(int index) { return index / columns * ROW_HEIGHT; }
    public boolean visible(int index, int widgetY, int top, int bottom) {
        return index >= 0 && index < count && widgetY + y(index) < bottom
                && widgetY + y(index) + CARD_HEIGHT > top;
    }
    public int indexAt(double localX, double localY) {
        if (localX < 0 || localY < 0 || localX >= width || localY >= height()) return -1;
        int column = (int) localX / (cardWidth + GAP);
        int row = (int) localY / ROW_HEIGHT;
        if (column >= columns || localX - column * (cardWidth + GAP) >= cardWidth
                || localY - row * ROW_HEIGHT >= CARD_HEIGHT) return -1;
        int index = row * columns + column;
        return index < count ? index : -1;
    }
    public int move(int index, int key) {
        if (count == 0) return -1;
        index = Math.max(0, Math.min(count - 1, index));
        return switch (key) {
            case 263 -> Math.max(0, index - 1);
            case 262 -> Math.min(count - 1, index + 1);
            case 265 -> index >= columns ? index - columns : index;
            case 264 -> index / columns < rows - 1 ? Math.min(count - 1, index + columns) : index;
            case 268 -> 0;
            case 269 -> count - 1;
            default -> index;
        };
    }
}
