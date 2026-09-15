package top.elysianherd.magicaland.client.gui.ponycustom;

record ThumbnailSize(int width, int height) {
    static ThumbnailSize of(int width, int height) {
        double scale = Math.min(2.0, 192.0 / Math.max(width, height));
        return new ThumbnailSize(Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale)));
    }

    Area fit(int targetWidth, int targetHeight) {
        float scale = Math.min(targetWidth / (float) width, targetHeight / (float) height);
        float drawnWidth = width * scale, drawnHeight = height * scale;
        return new Area((targetWidth - drawnWidth) / 2, (targetHeight - drawnHeight) / 2, drawnWidth, drawnHeight);
    }

    record Area(float x, float y, float width, float height) {}
}
