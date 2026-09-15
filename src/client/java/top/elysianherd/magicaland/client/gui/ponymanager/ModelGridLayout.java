package top.elysianherd.magicaland.client.gui.ponymanager;

public record ModelGridLayout(int width, int count, int columns, int cardWidth, int rows) {
    public static final int CARD_HEIGHT = 88;
    public static final int GAP = 6;
    public static final int ROW_HEIGHT = CARD_HEIGHT + GAP;

    public static ModelGridLayout of(int width, int count) {
        width = Math.max(1, width);
        count = Math.max(0, count);
        int columns = Math.max(1, Math.min(6, (width + GAP) / 96));
        int cardWidth = Math.max(1, (width - (columns - 1) * GAP) / columns);
        return new ModelGridLayout(width, count, columns, cardWidth, (count + columns - 1) / columns);
    }

    public int height() { return Math.max(0, rows * ROW_HEIGHT - GAP); }
    public int x(int index) { return index % columns * (cardWidth + GAP); }
    public int y(int index) { return index / columns * ROW_HEIGHT; }

    public int indexAt(double x, double y) {
        if (x < 0 || y < 0 || x >= width || y >= height()) return -1;
        int column = (int) x / (cardWidth + GAP);
        int row = (int) y / ROW_HEIGHT;
        if (column >= columns || x - column * (cardWidth + GAP) >= cardWidth
                || y - row * ROW_HEIGHT >= CARD_HEIGHT) return -1;
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
