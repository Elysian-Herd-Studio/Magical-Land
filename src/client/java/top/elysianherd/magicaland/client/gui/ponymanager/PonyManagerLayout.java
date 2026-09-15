package top.elysianherd.magicaland.client.gui.ponymanager;

import top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;

public record PonyManagerLayout(Rect preview, Rect model, Rect grid, Rect create, Rect duplicate,
        Rect rename, Rect delete, Rect customize) {
    public static PonyManagerLayout of(int width, int height) {
        int leftWidth = (width - 24) / 2;
        Rect preview = new Rect(8, 34, leftWidth, height - 42);
        Rect model = new Rect(12, 58, leftWidth - 8, Math.max(20, height - 86));
        int rightX = preview.right() + 8;
        int rightWidth = width - rightX - 8;
        int half = (rightWidth - 4) / 2;
        return new PonyManagerLayout(preview, model,
                new Rect(rightX, 56, rightWidth, Math.max(20, height - 140)),
                new Rect(rightX, height - 80, half, 20),
                new Rect(rightX + half + 4, height - 80, rightWidth - half - 4, 20),
                new Rect(rightX, height - 56, half, 20),
                new Rect(rightX + half + 4, height - 56, rightWidth - half - 4, 20),
                new Rect(rightX, height - 30, rightWidth, 22));
    }
}
