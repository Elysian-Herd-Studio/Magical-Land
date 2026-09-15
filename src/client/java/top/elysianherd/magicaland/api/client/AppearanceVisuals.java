package top.elysianherd.magicaland.api.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import java.util.UUID;
import top.elysianherd.magicaland.client.api.AppearanceVisualBridge;

/** Visual entry points. Drawing methods require the render thread and the caller's matrices and buffers. */
public final class AppearanceVisuals {
    private AppearanceVisuals() {}

    /** Client-thread, current-world transformation burst using the player's applied colors. Since API 1.4. */
    public static void playTransformation(UUID player) {
        AppearanceVisualBridge.playTransformation(player);
    }

    public static void renderOrb(MatrixStack matrices, int magicColor, double ticks, int seed) {
        AppearanceVisualBridge.renderOrb(matrices, magicColor, ticks, seed);
    }

    /** Draws a world-space flame anchored by the caller, with a tail driven by source motion. */
    public static void renderFlame(MatrixStack matrices, Entity source, int magicColor, float tickDelta) {
        AppearanceVisualBridge.renderFlame(matrices, source, magicColor, tickDelta);
    }

    public static void renderGlowingItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
            VertexConsumerProvider buffers, World world, int light, int seed, int magicColor) {
        AppearanceVisualBridge.renderGlowingItem(stack, mode, matrices, buffers, world, light, seed, magicColor);
    }

    /** Runs one non-nested temporary hand pass and restores its scope even when rendering throws. */
    public static void renderFirstPerson(LivingEntity owner, Entity camera, ItemStack stack,
            VertexConsumerProvider.Immediate buffers, Runnable render) {
        AppearanceVisualBridge.renderFirstPerson(owner, camera, stack, buffers, render);
    }

    /** Runs a hand pass with independent pose, equip and action state. */
    public static void renderFirstPerson(LivingEntity owner, ItemVisualContext context,
            VertexConsumerProvider.Immediate buffers, Runnable render) {
        AppearanceVisualBridge.renderFirstPerson(owner, context, buffers, render);
    }

    /** Adds visual inertia, the existing item glow and trail; restores the caller's matrices. */
    public static void renderLevitatingItem(LivingEntity owner, ItemVisualContext context,
            ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider buffers,
            World world, int light, int seed, int magicColor, float tickDelta) {
        AppearanceVisualBridge.renderLevitatingItem(owner, context, mode, matrices, buffers,
                world, light, seed, magicColor, tickDelta);
    }
}
