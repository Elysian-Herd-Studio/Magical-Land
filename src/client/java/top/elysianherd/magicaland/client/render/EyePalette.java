package top.elysianherd.magicaland.client.render;

public final class EyePalette {
    private EyePalette() {}

    public static int recolorAbgr(int pixel, int x, int y, int width, int height,
            int base, int light, int black, int sclera) {
        if ((pixel >>> 24) == 0) return pixel;
        int iris = IrisPalette.region(x, y, width, height);
        if (iris != 0) return IrisPalette.recolorAbgr(pixel, iris, base, light);
        double u = (x + 0.5) * 128 / width, v = (y + 0.5) * 128 / height;
        boolean white = u < 0.5 && v >= 62 && v < 63;
        boolean dark = u >= 8 && u < 16 && v < 10;
        if (!white && !dark) return pixel;
        int rgb = (pixel & 255) << 16 | (pixel & 0xFF00) | (pixel >>> 16 & 255);
        int color = white ? sclera : black;
        int result = 0;
        for (int shift : new int[] {0, 8, 16}) {
            int source = rgb >>> shift & 255, target = color >>> shift & 255;
            int channel = white ? (source * target + 127) / 255 : target + ((255 - target) * source + 127) / 255;
            result |= channel << shift;
        }
        return (pixel & 0xFF000000) | (result & 255) << 16 | (result & 0xFF00) | (result >>> 16 & 255);
    }
}
