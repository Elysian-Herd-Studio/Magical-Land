package top.elysianherd.magicaland.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.render.GlowingItem;
import top.elysianherd.magicaland.client.render.ItemLevitation;
import top.elysianherd.magicaland.client.render.LevitationTrail;
import top.elysianherd.magicaland.client.render.MagicEquip;
import top.elysianherd.magicaland.client.render.MagicEquipMotion;
import net.minecraft.util.Hand;

/**
 * 第一人称手持物品渲染
 * 原版物品动作上叠加受限魔法惯性；不改变实际物品使用。
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Unique
    private final GlowingItem magicItemRenderer = new GlowingItem();

    @Unique
    private boolean magicaland$equipPose;
    @Unique private boolean magicaland$equipMainHand;
    @Unique private float magicaland$equipDelta;
    @Unique private boolean magicaland$mapScalePose;

    @ModifyArgs(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void magicaland$emptyHandTransition(Args args) {
        AbstractClientPlayerEntity player = args.get(0);
        float delta = args.get(1);
        boolean main = args.get(3) == Hand.MAIN_HAND;
        if (!Config.getInstance().firstPersonMagicGlow
                || !MagicEquip.overrideFirstPerson(player, main, delta, args.get(5), args.get(6))) return;
        ItemStack visual = MagicEquip.visualStack(player, main, delta);
        args.set(5, visual);
        args.set(6, 0f);
        if ((main ? player.getMainHandStack() : player.getOffHandStack()).isEmpty()) args.set(4, 0f);
    }

    @ModifyArgs(method = "renderMapInBothHands", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonMap(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/item/ItemStack;)V"))
    private void magicaland$retainedMap(Args args) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !Config.getInstance().firstPersonMagicGlow
                || (!MagicEquip.instantSwap(client.player, client.getTickDelta())
                && !MagicEquip.transitioning(client.player, true, client.getTickDelta())
                && !MagicEquip.firstPersonHandoff(client.player, true, client.getTickDelta()))) return;
        ItemStack visual = MagicEquip.visualStack(client.player, true, client.getTickDelta());
        if (visual.isOf(Items.FILLED_MAP)) args.set(3, visual);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void magicaland$beginEquipPose(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
            float swing, ItemStack stack, float equip, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            CallbackInfo ci) {
        magicaland$equipMainHand = hand == Hand.MAIN_HAND;
        magicaland$equipDelta = delta;
        magicaland$equipPose = Config.getInstance().firstPersonMagicGlow && MagicEquip.enabled(player)
                && MagicEquip.transitioning(player, hand == Hand.MAIN_HAND, delta);
        if (!magicaland$equipPose) return;
        matrices.push();
        boolean left = (hand == Hand.MAIN_HAND) == (player.getMainArm() == net.minecraft.util.Arm.LEFT);
        MagicEquip.firstPersonPath(player, hand == Hand.MAIN_HAND, left, matrices, delta);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void magicaland$endEquipPose(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
            float swing, ItemStack stack, float equip, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            CallbackInfo ci) {
        if (magicaland$equipPose) matrices.pop();
        magicaland$equipPose = false;
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), cancellable = true)
    private void magicaland$beginMapScale(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            ItemStack stack, CallbackInfo ci) {
        magicaland$mapScalePose = false;
        if (!magicaland$equipPose) return;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        float scale = MagicEquip.scale(player, magicaland$equipMainHand, magicaland$equipDelta);
        if (scale <= MagicEquipMotion.MIN_VISIBLE_SCALE) { ci.cancel(); return; }
        if (scale < 1) {
            matrices.push();
            matrices.scale(scale, scale, scale);
            magicaland$mapScalePose = true;
        }
    }

    @Inject(method = "renderFirstPersonMap", at = @At("RETURN"))
    private void magicaland$endMapScale(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            ItemStack stack, CallbackInfo ci) {
        if (magicaland$mapScalePose) matrices.pop();
        magicaland$mapScalePose = false;
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
    private void magicaland$beginHandAura(float delta, MatrixStack matrices, VertexConsumerProvider.Immediate buffers,
            ClientPlayerEntity player, int light, CallbackInfo ci) {
        GlowingItem.beginFirstPersonPass();
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("TAIL"))
    private void magicaland$endHandAura(float delta, MatrixStack matrices, VertexConsumerProvider.Immediate buffers,
            ClientPlayerEntity player, int light, CallbackInfo ci) {
        GlowingItem.endFirstPersonPass(buffers);
    }

    @Redirect(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V"))
    private void redirectRenderItem(ItemRenderer instance, LivingEntity entity, ItemStack item,
            ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, World world, int light, int overlay, int seed) {
        if (renderMode.isFirstPerson() && Config.getInstance().firstPersonMagicGlow && MagicEquip.enabled(entity)) {
            boolean main = magicaland$isMainHand(entity, leftHanded);
            if (MagicEquip.visualStack(entity, main, MinecraftClient.getInstance().getTickDelta()).isEmpty()) return;
            if (MagicEquip.scale(entity, main, MinecraftClient.getInstance().getTickDelta()) <= MagicEquipMotion.MIN_VISIBLE_SCALE) return;
        }
        boolean isThirdPerson = renderMode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || renderMode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        boolean isTridentUsing = isThirdPerson && item.isOf(Items.TRIDENT) && entity.isUsingItem()
                && entity.getActiveItem() == item;

        matrices.push();

        if (isTridentUsing && entity instanceof AbstractClientPlayerEntity && Config.getInstance().replacePlayerModel) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
        }

        // 获取当前模型配置，检查 showHorn 设置
        ModelConfig modelConfig = AppearanceAnatomy.apply(
                entity.getUuid(), ModelManager.getAppliedModel());
        boolean enableHornEffect = modelConfig == null || modelConfig.showHorn;

        if (entity instanceof AbstractClientPlayerEntity && Config.getInstance().replacePlayerModel && enableHornEffect) {
            boolean isFirstPerson = renderMode.isFirstPerson();

            if (isFirstPerson && !Config.getInstance().firstPersonMagicGlow) {
                ItemLevitation.forget(entity, magicaland$isMainHand(entity, leftHanded), true);
                instance.renderItem(entity, item, renderMode, leftHanded, matrices, vertexConsumers, world, light,
                        overlay, seed);
            } else {
                matrices.push();
                LevitationTrail trail = LevitationTrail.EMPTY;
                if (isFirstPerson) {
                    double xOffset = leftHanded ? -0.15 : 0.15;
                    matrices.translate(xOffset, 0.2, -0.4);
                    trail = ItemLevitation.applyFirstPerson(entity, item, magicaland$isMainHand(entity, leftHanded),
                            leftHanded, matrices, MinecraftClient.getInstance().getTickDelta());
                }
                magicItemRenderer.renderItemWithGlow(
                        instance, entity, item, renderMode, leftHanded, matrices, vertexConsumers, world, light, seed,
                        GlowingItem.getCurrentGlowColor(), true, trail);
                matrices.pop();
            }
        } else {
            if (renderMode.isFirstPerson()) ItemLevitation.forget(entity, magicaland$isMainHand(entity, leftHanded), true);
            instance.renderItem(entity, item, renderMode, leftHanded, matrices, vertexConsumers, world, light, overlay,
                    seed);
        }

        matrices.pop();
    }

    @Unique
    private static boolean magicaland$isMainHand(LivingEntity entity, boolean left) {
        return left == (entity.getMainArm() == net.minecraft.util.Arm.LEFT);
    }
}
