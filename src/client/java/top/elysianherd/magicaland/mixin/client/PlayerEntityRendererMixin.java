package top.elysianherd.magicaland.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.model.GeckoPlayerAnimatable;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.client.render.*;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.network.NetworkHandler;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin
    extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    @Unique
    private final Map<UUID, GeckoPlayerAnimatable> ponyAnimatables = new HashMap<>();

    @Unique
    private final Map<UUID, PonyRenderer> ponyRenderers = new HashMap<>();

    @Unique
    private ClientWorld trackedWorld;

    @Unique
    private int playerStateCleanupTimer;

    @Unique
    private boolean magicaland$featuresOnly;

    @Shadow
    private void setModelPose(AbstractClientPlayerEntity player) {}

    @Unique
    private static final Map<UUID, Float> flightRolls = new HashMap<>();

    public PlayerEntityRendererMixin(EntityRendererFactory.Context ctx,
            PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    /**
     * 核心渲染注入：决定使用 pony 模型还是原版模型。
     * 
     * 判定逻辑：
     * 1. replacePlayerModel=false → 走原版渲染（不取消）
     * 2. 渲染的是本地玩家 → 使用本地活跃模型
     * 3. 渲染的是远程玩家：
     *    a. 服务端有mod 且 该玩家有远程模型配置 → 使用远程模型
     *    b. 服务端无mod 或 该玩家无模型 → 走原版渲染（不取消）
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(AbstractClientPlayerEntity player, float f, float g, MatrixStack matrixStack,
            VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        cleanupPlayerStates();
        if (!Config.getInstance().replacePlayerModel) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        boolean isSelf = client.player != null && player.getUuid().equals(client.player.getUuid());
        GeckoPlayerAnimatable ponyAnimatable = ponyAnimatables.computeIfAbsent(player.getUuid(),
                ignored -> new GeckoPlayerAnimatable());
        PonyRenderer ponyRenderer = ponyRenderers.computeIfAbsent(player.getUuid(), ignored -> new PonyRenderer());

        ModelConfig configToUse;
        if (isSelf) {
            configToUse = ModelManager.getAppliedModel();
            if (configToUse == null) {
                return;
            }
            ponyRenderer.clearOverride();
        } else {
            if (NetworkHandler.serverHasMod && ClientNetworkHandler.remoteModels.containsKey(player.getUuid())) {
                configToUse = ClientNetworkHandler.remoteModels.get(player.getUuid());
                ponyRenderer.setOverrideConfig(configToUse);
            } else {
                return;
            }
        }

        configToUse = AppearanceAnatomy.apply(player.getUuid(), configToUse);
        ponyRenderer.setOverrideConfig(configToUse);
        ponyAnimatable.setPlayer(player);

        PonyVisibility visibility = PonyRenderer.visibilityFor(player, isVisible(player));
        if (visibility == PonyVisibility.HIDDEN) {
            // 原版仍需绘制隐身玩家的装备和持握物。
            ponyRenderer.clearOverride();
            return;
        }

        matrixStack.push();

        org.joml.Matrix4f gazeFrame = new org.joml.Matrix4f(matrixStack.peek().getPositionMatrix());

        var wingPose = ponyAnimatable.isPlayingEmote() ? null : PonyRenderer.wingFlightPoseFor(player);

        if (wingPose != null) {
            flightRolls.remove(player.getUuid());
            matrixStack.translate(0, .8, 0);
            matrixStack.multiply(PonyWingFlightMath.rotation(wingPose));
            matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrixStack.translate(0, -.8, 0);
        } else if (player.isSleeping()) {
            net.minecraft.util.math.Direction direction = player.getSleepingDirection();
            if (direction != null) {
                float sleepYaw = direction.asRotation();
                matrixStack.multiply(
                        net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(270.0F - sleepYaw));
                matrixStack.translate(-1.7, -0.1, 0.0);
                matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(270.0F));
                matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            } else {
                float bodyYaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(g, player.prevBodyYaw,
                        player.bodyYaw);
                matrixStack
                        .multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
            }
        } else {
            float bodyYaw = PonyBodyYaw.sample(player, g);
            matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));

            if (!configToUse.showWings) {
                flightRolls.remove(player.getUuid());
            } else if (player.getAbilities().flying && player.isSprinting()) {
                float yawDelta = net.minecraft.util.math.MathHelper
                        .wrapDegrees(player.bodyYaw - player.prevBodyYaw);
                float targetRoll = net.minecraft.util.math.MathHelper.clamp(yawDelta * -2.5F, -30.0F, 30.0F);
                float currentRoll = flightRolls.getOrDefault(player.getUuid(), 0.0F);
                currentRoll = net.minecraft.util.math.MathHelper.lerp(0.15F, currentRoll, targetRoll);
                flightRolls.put(player.getUuid(), currentRoll);

                matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(currentRoll));
            } else if (flightRolls.containsKey(player.getUuid())) {
                float currentRoll = flightRolls.get(player.getUuid());
                currentRoll = net.minecraft.util.math.MathHelper.lerp(0.15F, currentRoll, 0.0F);
                if (Math.abs(currentRoll) < 0.1F) {
                    flightRolls.remove(player.getUuid());
                } else {
                    flightRolls.put(player.getUuid(), currentRoll);
                    matrixStack
                            .multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(currentRoll));
                }
            }
        }

        double yOffset = -0.5;
        if (player.hasVehicle()) {
            net.minecraft.entity.Entity vehicle = player.getVehicle();
            if (vehicle instanceof net.minecraft.entity.passive.PigEntity) {
                yOffset -= 0.08;
            } else if (vehicle instanceof net.minecraft.entity.passive.AbstractHorseEntity) {
                yOffset -= 0.06;
            } else if (vehicle instanceof net.minecraft.entity.vehicle.BoatEntity
                    || vehicle instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity) {
                yOffset += 0.4;
            }
        }

        matrixStack.translate(-0.5, yOffset, -0.5);

        ponyRenderer.setBodyVisibility(visibility);
        ponyRenderer.setGazeFrame(gazeFrame, g);
        ponyRenderer.setFlightFrame(PonyFlightVisuals.sample(player, configToUse, g));
        ponyRenderer.setWingFlightPose(wingPose);
        try {
            RenderLayer renderLayer = ponyRenderer.getRenderType(ponyAnimatable,
                    ponyRenderer.getTextureLocation(ponyAnimatable), vertexConsumerProvider, g);
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
            ponyRenderer.render(matrixStack, ponyAnimatable, vertexConsumerProvider, renderLayer,
                    vertexConsumer, i);
        } finally {
            ponyRenderer.setBodyVisibility(PonyVisibility.VISIBLE);
            ponyRenderer.setGazeFrame(null, 0);
            ponyRenderer.setFlightFrame(PonyFlightVisuals.Frame.NONE);
            ponyRenderer.setWingFlightPose(null);
        }

        if (visibility == PonyVisibility.VISIBLE)
            this.renderMagicHeldItem(player, configToUse, matrixStack, vertexConsumerProvider, i, g, gazeFrame, ponyRenderer);

        matrixStack.pop();

        if (visibility != PonyVisibility.VISIBLE) {
            boolean previousFeaturesOnly = magicaland$featuresOnly;
            magicaland$featuresOnly = true;
            try {
                setModelPose(player);
                super.render(player, f, g, matrixStack, vertexConsumerProvider, i);
            } finally {
                magicaland$featuresOnly = previousFeaturesOnly;
            }
        } else if (this.hasLabel(player)) {
            this.renderLabelIfPresent(player, player.getDisplayName(), matrixStack, vertexConsumerProvider, i);
        }

        ponyRenderer.clearOverride();

        ci.cancel();
    }

    @Override
    protected RenderLayer getRenderLayer(AbstractClientPlayerEntity player, boolean visible, boolean translucent,
            boolean outline) {
        return magicaland$featuresOnly ? null : super.getRenderLayer(player, visible, translucent, outline);
    }

    @Unique
    private void cleanupPlayerStates() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != trackedWorld) {
            ponyAnimatables.clear();
            ponyRenderers.clear();
            flightRolls.clear();
            trackedWorld = client.world;
            playerStateCleanupTimer = 0;
            return;
        }
        if (client.world == null || ++playerStateCleanupTimer < 100) {
            return;
        }
        playerStateCleanupTimer = 0;

        Set<UUID> loadedPlayers = new HashSet<>();
        for (AbstractClientPlayerEntity loadedPlayer : client.world.getPlayers()) {
            loadedPlayers.add(loadedPlayer.getUuid());
        }
        ponyAnimatables.keySet().removeIf(uuid -> !loadedPlayers.contains(uuid));
        ponyRenderers.keySet().removeIf(uuid -> !loadedPlayers.contains(uuid));
        flightRolls.keySet().removeIf(uuid -> !loadedPlayers.contains(uuid));
    }

    @Unique
    private final GlowingItem magicItemRenderer = new GlowingItem();

    @Unique
    private void renderMagicHeldItem(AbstractClientPlayerEntity player, ModelConfig modelConfig, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta, org.joml.Matrix4f entityFrame, PonyRenderer ponyRenderer) {
        boolean enableHornEffect = modelConfig == null || modelConfig.showHorn;

        // 如果 showHorn 为 false，不渲染发光手持物品
        if (!enableHornEffect) {
            ItemLevitation.forget(player, true, false);
            ItemLevitation.forget(player, false, false);
            return;
        }

        net.minecraft.item.ItemStack mainHandStack = MagicEquip.visualStack(player, true, tickDelta);
        net.minecraft.item.ItemStack offHandStack = MagicEquip.visualStack(player, false, tickDelta);

        if (mainHandStack.isEmpty() && offHandStack.isEmpty())
            return;

        float swingProgress = player.getHandSwingProgress(tickDelta);
        net.minecraft.util.Arm mainArm = player.getMainArm();

        boolean isSneaking = player.isSneaking();
        float pitch = player.getPitch(tickDelta);

        if (!mainHandStack.isEmpty()) {
            boolean isRightArm = mainArm == net.minecraft.util.Arm.RIGHT;
            renderHandItem(player, modelConfig, mainHandStack, matrices, vertexConsumers, light, tickDelta, true,
                    isRightArm, isSneaking, swingProgress, pitch, entityFrame, ponyRenderer);
        }

        if (!offHandStack.isEmpty()) {
            boolean isRightArm = mainArm == net.minecraft.util.Arm.LEFT;
            renderHandItem(player, modelConfig, offHandStack, matrices, vertexConsumers, light, tickDelta, false,
                    isRightArm, isSneaking, swingProgress, pitch, entityFrame, ponyRenderer);
        }
    }

    @Unique
    private void renderHandItem(AbstractClientPlayerEntity player, ModelConfig modelConfig,
            net.minecraft.item.ItemStack stack,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta,
            boolean isMainHand, boolean isRightArm, boolean isSneaking,
            float swingProgress, float pitch, org.joml.Matrix4f entityFrame, PonyRenderer ponyRenderer) {
        if (MagicEquip.scale(player, isMainHand, tickDelta) <= MagicEquipMotion.MIN_VISIBLE_SCALE) return;
        matrices.push();

        if (isSneaking) {
            matrices.translate(0.0, -0.2, 0.0);
            matrices.translate(0.5, 1.0, 0.5);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(28.6F));
            matrices.translate(-0.5, -1.0, -0.5);
        }

        float pivotX = isRightArm ? 1.0F : 0.0F;
        float pivotY = 1.4F;
        float pivotZ = 0.0F;
        matrices.translate(pivotX, pivotY, pivotZ);
        org.joml.Matrix4f anchorFrame = new org.joml.Matrix4f(matrices.peek().getPositionMatrix())
                .translate(isRightArm ? .2f : -.2f, 0, -.4f);
        var entrance = MagicEquipMotion.offset(MagicEquip.progress(player, isMainHand, tickDelta), !isRightArm, false);
        matrices.translate(entrance.x(), entrance.y(), entrance.z());

        float armPitch = 0.0F;
        float armYaw = 0.0F;
        float armRoll = 0.0F;

        if (player.hasVehicle()) {
            armPitch = -0.62F;
        }

        armPitch += pitch * ((float) Math.PI / 180F) * 0.1F;

        if (swingProgress > 0.0F) {
            net.minecraft.util.Hand activeHand = player.preferredHand;
            boolean isSwingingArm = (activeHand == net.minecraft.util.Hand.MAIN_HAND && isMainHand)
                    || (activeHand == net.minecraft.util.Hand.OFF_HAND && !isMainHand);
            if (activeHand == null) {
                isSwingingArm = isMainHand;
            }

            if (isSwingingArm) {
                float swing1 = net.minecraft.util.math.MathHelper.sin(swingProgress * (float) Math.PI);
                float swing2 = net.minecraft.util.math.MathHelper
                        .sin(net.minecraft.util.math.MathHelper.sqrt(swingProgress) * (float) Math.PI);
                armPitch -= swing2 * 1.2F + swing1 * 0.4F;
                armYaw += isRightArm ? swing2 * 0.4F : -swing2 * 0.4F;
                armRoll += isRightArm ? swing1 * 0.2F : -swing1 * 0.2F;
            }
        }

        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(armRoll));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation(armYaw));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotation(armPitch));

        float offsetX = isRightArm ? 0.2F : -0.2F;
        float offsetY = 0.0F;
        float offsetZ = -0.4F;
        matrices.translate(offsetX, offsetY, offsetZ);

        boolean isTridentUsing = stack.isOf(net.minecraft.item.Items.TRIDENT) && player.isUsingItem()
                && player.getActiveItem() == stack;
        if (isTridentUsing && Config.getInstance().replacePlayerModel) {
            matrices.translate(0.0, 1.0, 0.0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
        }
        int glowColor = GlowingItem.getGlowColor(modelConfig);
        net.minecraft.client.render.item.ItemRenderer itemRenderer = net.minecraft.client.MinecraftClient.getInstance()
                .getItemRenderer();

        net.minecraft.client.render.model.json.ModelTransformationMode mode = isRightArm
                ? net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                : net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_LEFT_HAND;

        var consumption = ponyRenderer.magicConsumption(isMainHand);
        if (consumption.weight() > 0) {
            var display = itemRenderer.getModel(stack, player.getWorld(), player,
                    net.minecraft.client.render.OverlayTexture.DEFAULT_UV).getTransformation().getTransformation(mode);
            var translation = new org.joml.Vector3f(display.translation);
            if (!isRightArm) translation.x = -translation.x;
            PonyMagicConsumption.apply(matrices, anchorFrame,
                    ponyRenderer.magicMouthFrame(), consumption, translation);
        }
        LevitationTrail trail = ItemLevitation.applyWorld(player, stack, isMainHand, !isRightArm,
                matrices, entityFrame, anchorFrame, tickDelta);

        magicItemRenderer.renderItemWithGlow(
                itemRenderer, player, stack,
                mode,
                !isRightArm, matrices, vertexConsumers, player.getWorld(),
                light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, glowColor, true, trail);

        matrices.pop();
    }

    @Inject(method = "renderRightArm", at = @At("HEAD"), cancellable = true)
    private void onRenderRightArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            AbstractClientPlayerEntity player, CallbackInfo ci) {
        ModelConfig modelConfig = AppearanceAnatomy.apply(
                player.getUuid(), ModelManager.getAppliedModel());

        // 小马第一人称不显示人类手臂，物品动作仍交给原版。
        if (Config.getInstance().replacePlayerModel && modelConfig != null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"), cancellable = true)
    private void onRenderLeftArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            AbstractClientPlayerEntity player, CallbackInfo ci) {
        ModelConfig modelConfig = AppearanceAnatomy.apply(
                player.getUuid(), ModelManager.getAppliedModel());

        // 小马第一人称不显示人类手臂，物品动作仍交给原版。
        if (Config.getInstance().replacePlayerModel && modelConfig != null) {
            ci.cancel();
        }
    }
}
