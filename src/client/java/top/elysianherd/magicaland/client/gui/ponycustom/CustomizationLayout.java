package top.elysianherd.magicaland.client.gui.ponycustom;

public record CustomizationLayout(Rect preview, Rect model, Rect details, int tabsY, int tabColumns,
        int tabWidth) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public static CustomizationLayout of(int x, int y, int width, int height) {
        int columns = Math.min(6, Math.max(3, (width + 4) / 49));
        int rows = (6 + columns - 1) / columns;
        int top = y + rows * 23 + 5;
        int bottom = y + height;
        int leftWidth = Math.min(270, Math.max(104, (width - 8) * 36 / 100));
        leftWidth = Math.min(leftWidth, Math.max(80, width - 158));
        int panelHeight = Math.max(80, bottom - top);
        Rect preview = new Rect(x, top, leftWidth, panelHeight);
        Rect model = new Rect(x + 4, top + 30, leftWidth - 8, Math.max(20, panelHeight - 34));
        Rect details = new Rect(preview.right() + 8, top, width - leftWidth - 8, panelHeight);
        return new CustomizationLayout(preview, model, details, y, columns, (width + 4) / columns - 4);
    }
}
