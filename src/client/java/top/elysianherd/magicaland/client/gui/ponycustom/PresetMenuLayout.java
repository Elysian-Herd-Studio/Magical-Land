package top.elysianherd.magicaland.client.gui.ponycustom;

public record PresetMenuLayout(int top, int rows, int rowHeight) {
    public record Header(int dropdownWidth, int createX, int deleteX, int buttonWidth) {}
    public static Header header(int width) {
        int button = width < 140 ? 18 : 20;
        int dropdown = width - (button + 3) * 2;
        return new Header(dropdown, dropdown + 3, width - button, button);
    }
    public static PresetMenuLayout of(int headerY, int headerHeight, int screenHeight, int presets) {
        int below = Math.max(0, screenHeight - headerY - headerHeight - 6);
        int above = Math.max(0, headerY - 6);
        boolean upward = below < 60 && above > below;
        int space = upward ? above : below;
        int rows = Math.min(Math.max(0, presets), Math.min(8, Math.max(1, space / 20 - 1)));
        int height = (rows + 1) * 20;
        int top = upward ? headerY - height - 2 : headerY + headerHeight + 2;
        top = Math.max(2, Math.min(top, Math.max(2, screenHeight - height - 2)));
        return new PresetMenuLayout(top, rows, 20);
    }
    public int height() { return (rows + 1) * rowHeight; }
    public int rowAt(double y) {
        if (!Double.isFinite(y) || y < top || y >= top + height()) return -1;
        return (int) ((y - top) / rowHeight);
    }
}
