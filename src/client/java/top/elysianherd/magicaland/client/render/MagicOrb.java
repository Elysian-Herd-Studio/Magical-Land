package top.elysianherd.magicaland.client.render;

import net.minecraft.client.util.math.MatrixStack;

/** 保留旧 API；没有来源实体时使用静止柔焰。 */
public final class MagicOrb {
    private MagicOrb() {}
    public static void render(MatrixStack matrices,int color,double ticks,int seed) {
        MagicFlame.stationary(matrices, color, ticks, seed);
    }
}
