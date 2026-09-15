package top.elysianherd.magicaland.client.api;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import top.elysianherd.magicaland.api.client.ItemVisualContext;
import top.elysianherd.magicaland.client.render.GlowingItem;
import top.elysianherd.magicaland.client.render.ItemLevitation;
import top.elysianherd.magicaland.client.render.MagicEquipMotion;
import top.elysianherd.magicaland.client.render.MagicOrb;
import top.elysianherd.magicaland.client.render.MagicFlame;
import top.elysianherd.magicaland.client.render.TransformationParticles;

public final class AppearanceVisualBridge {
    private static boolean firstPersonPass;
    private static final GlowingItem ITEMS = new GlowingItem();

    private AppearanceVisualBridge() {}

    public static void playTransformation(UUID playerId) {
        Objects.requireNonNull(playerId, "player");
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        var player = client.world.getPlayerByUuid(playerId);
        if (player == null || player.isRemoved() || player.getWorld() != client.world) return;
        if (AppearanceAccess.find(playerId).filter(value -> value.modelReplacementEnabled()).isEmpty()) return;
        TransformationParticles.play(player, AppearanceAnatomy.forPlayer(player));
    }

    public static void renderOrb(MatrixStack matrices, int color, double ticks, int seed) {
        MagicOrb.render(matrices, color, ticks, seed);
    }

    public static void renderFlame(MatrixStack matrices, Entity source, int color, float delta) {
        Objects.requireNonNull(matrices, "matrices");
        Objects.requireNonNull(source, "source");
        if (!Float.isFinite(delta) || delta < 0 || delta > 1)
            throw new IllegalArgumentException("tickDelta must be finite and between 0 and 1");
        if (source.isRemoved() || source.getWorld() != MinecraftClient.getInstance().world) return;
        matrices.push();
        try {
            MagicFlame.render(matrices, source, color, delta);
        } finally {
            matrices.pop();
        }
    }

    public static void renderGlowingItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
            VertexConsumerProvider buffers, World world, int light, int seed, int color) {
        GlowingItem.renderPreviewWithGlow(MinecraftClient.getInstance().getItemRenderer(), stack, mode,
                matrices, buffers, world, light, seed, color);
    }

    public static void renderFirstPerson(LivingEntity owner, Entity camera, ItemStack stack,
            VertexConsumerProvider.Immediate buffers, Runnable render) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(stack, "stack");
        runFirstPerson(() -> FirstPersonItemView.open(owner, camera, stack), buffers, render);
    }

    public static void renderFirstPerson(LivingEntity owner, ItemVisualContext context,
            VertexConsumerProvider.Immediate buffers, Runnable render) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(context, "context");
        runFirstPerson(() -> FirstPersonItemView.open(owner, context), buffers, render);
    }

    private static void runFirstPerson(java.util.function.Supplier<FirstPersonItemView> open,
            VertexConsumerProvider.Immediate buffers, Runnable render) {
        Objects.requireNonNull(buffers, "buffers");
        Objects.requireNonNull(render, "render");
        if (firstPersonPass) throw new IllegalStateException("Temporary first-person passes cannot be nested");
        firstPersonPass = true;
        try (var view = open.get()) {
            GlowingItem.beginFirstPersonPass();
            try {
                render.run();
            } finally {
                GlowingItem.endFirstPersonPass(buffers);
            }
        } finally {
            firstPersonPass = false;
        }
    }

    public static void renderLevitatingItem(LivingEntity owner, ItemVisualContext context,
            ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider buffers,
            World world, int light, int seed, int color, float delta) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(matrices, "matrices");
        Objects.requireNonNull(buffers, "buffers");
        Objects.requireNonNull(world, "world");
        if (mode != ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                && mode != ModelTransformationMode.THIRD_PERSON_RIGHT_HAND)
            throw new IllegalArgumentException("Levitating items require a third-person hand mode");
        if (!Float.isFinite(delta) || delta < 0 || delta > 1)
            throw new IllegalArgumentException("tickDelta must be finite and between 0 and 1");
        if (owner.getWorld() != world || context.source().getWorld() != world)
            throw new IllegalArgumentException("Visual owner and source must belong to the render world");
        ItemStack stack = context.stack();
        boolean left = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
        matrices.push();
        try {
            var trail = ItemLevitation.applyVisualWorld(context, stack, left, matrices, delta);
            if (!stack.isEmpty() && MagicEquipMotion.scale(context.equipProgress()) > MagicEquipMotion.MIN_VISIBLE_SCALE)
                ITEMS.renderItemWithGlow(MinecraftClient.getInstance().getItemRenderer(), owner, stack,
                        mode, left, matrices, buffers, world, light, seed, color, true, trail);
        } finally {
            matrices.pop();
        }
    }
}
