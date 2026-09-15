package top.elysianherd.magicaland.client.render;

import top.elysianherd.magicaland.cutiemark.CutieMarkData;

/** 256² 身体图集上的两个独立外侧面，不改 UV 或增加透明几何。 */
public final class CutieMarkTexture {
    public enum Side {
        LEFT(88, 54), RIGHT(56, 78);
        public final int x, y;
        Side(int x, int y) { this.x = x; this.y = y; }
    }

    private CutieMarkTexture() {}

    public static Side sideForBone(String bone) {
        return "LHindLeg".equals(bone) ? Side.LEFT : "RHindLeg".equals(bone) ? Side.RIGHT : null;
    }

    public static int pixelIndex(Side side, int x, int y, int width, int height) {
        if (side == null || width != height || width < 256 || width % 256 != 0) return -1;
        int scale = width / 256;
        int dx = x - side.x * scale, dy = y - side.y * scale;
        if (dx < 0 || dy < 0 || dx >= CutieMarkData.SIZE * scale || dy >= CutieMarkData.SIZE * scale) return -1;
        // 画板到模型外侧面左右镜像：贴图列反向采样。
        return dy / scale * CutieMarkData.SIZE + (CutieMarkData.SIZE - 1 - dx / scale);
    }

    public static int composite(int sourceAbgr, int bodyAbgr, int markArgb) {
        if ((markArgb >>> 24) != 255 || (sourceAbgr >>> 24) == 0) return bodyAbgr;
        int shade = (54 * (sourceAbgr & 255) + 183 * (sourceAbgr >> 8 & 255)
                + 19 * (sourceAbgr >> 16 & 255) + 128) >> 8;
        int r = ((markArgb >> 16 & 255) * shade + 127) / 255;
        int g = ((markArgb >> 8 & 255) * shade + 127) / 255;
        int b = ((markArgb & 255) * shade + 127) / 255;
        return bodyAbgr & 0xFF000000 | b << 16 | g << 8 | r;
    }

    public static int legacyTint(int sourceAbgr, int color) {
        int r = ((sourceAbgr & 255) * (color >> 16 & 255) + 127) / 255;
        int g = ((sourceAbgr >> 8 & 255) * (color >> 8 & 255) + 127) / 255;
        int b = ((sourceAbgr >> 16 & 255) * (color & 255) + 127) / 255;
        return sourceAbgr & 0xFF000000 | b << 16 | g << 8 | r;
    }
}
