package top.elysianherd.magicaland.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.RenderUtils;
import top.elysianherd.magicaland.api.client.AppearanceOverrides;
import top.elysianherd.magicaland.client.animation.ClientGaze;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.animation.PonyEmotePose;
import top.elysianherd.magicaland.client.animation.PonyFlightAnimations;
import top.elysianherd.magicaland.client.animation.PonyWingFlightAnimations;
import top.elysianherd.magicaland.api.client.FlightPose;
import top.elysianherd.magicaland.client.emote.EmoteClient;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.model.GeckoPlayerAnimatable;
import top.elysianherd.magicaland.client.model.GeckoPlayerModel;

public class PonyRenderer extends GeoObjectRenderer<GeckoPlayerAnimatable> {

    private static final Identifier PONY_BASE = new Identifier("magicaland", "textures/entity/base.png");
    private static final Identifier PONY_TS = new Identifier("magicaland", "textures/entity/mane.png");

    private ModelConfig overrideConfig = null;
    private PonyVisibility bodyVisibility = PonyVisibility.VISIBLE;
    private boolean usingPalette;
    private boolean mirroredMane;
    private VertexConsumerProvider eyeBuffers;
    private RenderLayer eyeLayer, pupilLayer;
    private EyeApertureRender eyeAperture;
    private Matrix4f gazeFrame;
    private float gazePartialTick;
    private PonyFlightVisuals.Frame flightFrame = PonyFlightVisuals.Frame.NONE;
    private FlightPose wingFlightPose;
    private final PonyGazeMath.Smoother gazeSmoother = new PonyGazeMath.Smoother();
    private final PonyTurnGaze turnGaze = new PonyTurnGaze();
    private final PonyGuiGaze.Tracker guiGaze = new PonyGuiGaze.Tracker();
    private final HornGlowGeometry hornGlow = new HornGlowGeometry();
    private AuraCapture auraCapture;
    private BodyFlightAura.Capture bodyAuraCapture;
    private boolean headLookActive;
    private final PonyBackwardHeadPose backwardHeadPose = new PonyBackwardHeadPose();
    private PonyHeldItems.Frame heldItems = PonyHeldItems.Frame.NONE;
    private final PonyHeldItemPose.Weights heldWeights = new PonyHeldItemPose.Weights();
    private Matrix4f magicMouthFrame;
    private final PonyMagicConsumption magicConsumption = new PonyMagicConsumption();

    public Matrix4f magicMouthFrame() { return magicMouthFrame == null ? null : new Matrix4f(magicMouthFrame); }
    public PonyMagicConsumption.Pose magicConsumption(boolean main) { return magicConsumption.pose(main); }

    private static final class AuraCapture {
        final java.util.Set<RenderLayer> layers = new java.util.LinkedHashSet<>();
        final java.util.List<java.util.function.Consumer<VertexConsumer>> draws = new java.util.ArrayList<>();
    }

    @Override
    public void defaultRender(MatrixStack stack, GeckoPlayerAnimatable animatable, VertexConsumerProvider buffers,
            RenderLayer renderType, VertexConsumer buffer, float yaw, float partialTick, int light) {
        AuraCapture previous = auraCapture;
        magicMouthFrame = null;
        var player = animatable.getPlayer();
        var config = getEffectiveConfig();
        boolean magicUse = gazeFrame != null && config != null && config.showHorn && player != null
                && player.isAlive() && !player.isInvisible() && !player.isSpectator() && !player.isSleeping();
        if (magicUse) {
            var use = PonyHeldItems.consumption(player.isUsingItem(), player.getActiveHand(), player.getActiveItem(),
                    player.getItemUseTimeLeft(), partialTick);
            magicConsumption.update(player.age + (double) partialTick, use, true,
                    player.getMainHandStack().isEmpty() ? null : player.getMainHandStack().getItem(),
                    player.getOffHandStack().isEmpty() ? null : player.getOffHandStack().getItem());
        } else magicConsumption.reset();
        PonyHeldItems.Frame previousHeld = heldItems;
        heldItems = gazeFrame == null ? PonyHeldItems.Frame.NONE : PonyHeldItems.frame(animatable.getPlayer(), getEffectiveConfig(), partialTick);
        if (heldItems.player() != null) {
            PonyFlightAnimations.resolve(getGeoModel().getAnimation(animatable, "fly"));
            heldWeights.update(heldItems.player().age + (double) partialTick, heldItems);
        } else heldWeights.reset();
        boolean previousSoundPass = animatable.worldSoundPass();
        animatable.setWorldSoundPass(worldFlightRender());
        if (wingFlightPose != null && worldFlightRender())
            PonyWingFlightAnimations.resolve(getGeoModel().getAnimation(animatable, "fly"),
                    getGeoModel().getAnimation(animatable, "fall"), getGeoModel().getAnimation(animatable, "fall_transfer"));
        BodyFlightAura.Capture previousBody = bodyAuraCapture;
        AuraCapture capture = new AuraCapture();
        auraCapture = capture;
        bodyAuraCapture = beginBodyAura(animatable, partialTick);
        try {
            super.defaultRender(stack, animatable, buffers, renderType, buffer, yaw, partialTick, light);
            if (bodyAuraCapture != null) bodyAuraCapture.finish();
            if (!capture.draws.isEmpty()) {
                // 预览先提交整只小马；世界光晕另等全部实体与透明层完成。
                if (!HornAuraPass.isWorld() && buffers instanceof VertexConsumerProvider.Immediate immediate)
                    capture.layers.forEach(immediate::draw);
                HornAuraPass.submit(glow -> capture.draws.forEach(draw -> draw.accept(glow)));
            }
        } finally {
            heldItems = previousHeld;
            animatable.setWorldSoundPass(previousSoundPass);
            auraCapture = previous;
            bodyAuraCapture = previousBody;
        }
    }

    private BodyFlightAura.Capture beginBodyAura(GeckoPlayerAnimatable animatable, float partialTick) {
        var player = animatable.getPlayer();
        ModelConfig config = getEffectiveConfig();
        if (!worldFlightRender() || flightFrame.magic() <= 0
                || config == null || config.showWings || !config.showHorn || player == null
                || !player.isAlive() || player.isInvisible() || player.isSpectator()) return null;
        var camera = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera();
        if (player.squaredDistanceTo(camera.getPos()) > 48 * 48) return null;
        float strength = flightFrame.magic() * PonyFlightVisuals.auraBrightness(player, partialTick);
        return BodyFlightAura.begin(strength, GlowingItem.getGlowColor(config), player.age + (double) partialTick);
    }

    private boolean worldFlightRender() {
        return gazeFrame != null && HornAuraPass.isWorld() && PonyGuiGaze.current() == null;
    }

    public void setGazeFrame(Matrix4f frame, float partialTick) {
        gazeFrame = frame == null ? null : new Matrix4f(frame);
        gazePartialTick = partialTick;
    }

    public void setFlightFrame(PonyFlightVisuals.Frame frame) {
        flightFrame = frame == null ? PonyFlightVisuals.Frame.NONE : frame;
    }

    public void setWingFlightPose(FlightPose pose) { wingFlightPose = pose; }

    public static FlightPose wingFlightPoseFor(net.minecraft.client.network.AbstractClientPlayerEntity player) {
        return HornAuraPass.isWorld() && PonyGuiGaze.current() == null ? PonyFlightVisuals.wingPose(player) : null;
    }

    public PonyRenderer() {
        super(new GeckoPlayerModel());
    }

    public PonyRenderer(GeckoPlayerModel model) {
        super(model);
    }

    public static PonyVisibility visibilityFor(net.minecraft.client.network.AbstractClientPlayerEntity player,
            boolean visible) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        return PonyVisibility.select(PonyGuiGaze.current() != null, visible,
                !player.isInvisibleTo(client.player), client.hasOutline(player));
    }

    public void setBodyVisibility(PonyVisibility visibility) {
        bodyVisibility = visibility;
    }

    @Override
    public RenderLayer getRenderType(GeckoPlayerAnimatable animatable, Identifier texture,
            VertexConsumerProvider buffers, float partialTick) {
        return switch (bodyVisibility) {
            case TRANSLUCENT -> RenderLayer.getItemEntityTranslucentCull(texture);
            case OUTLINE -> RenderLayer.getOutline(texture);
            default -> super.getRenderType(animatable, texture, buffers, partialTick);
        };
    }

    @Override
    public Color getRenderColor(GeckoPlayerAnimatable animatable, float partialTick, int light) {
        return bodyVisibility == PonyVisibility.TRANSLUCENT ? Color.ofRGBA(1f, 1f, 1f, .15f)
                : super.getRenderColor(animatable, partialTick, light);
    }

    public void setOverrideConfig(ModelConfig config) {
        this.overrideConfig = config;
    }

    public void clearOverride() {
        this.overrideConfig = null;
    }

    private ModelConfig getEffectiveConfig() {
        if (overrideConfig != null) {
            return overrideConfig;
        }
        return ModelManager.getAppliedModel();
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, GeckoPlayerAnimatable animatable,
            GeoBone bone, RenderLayer renderType,
            VertexConsumerProvider bufferSource, VertexConsumer buffer,
            boolean isReRender, float partialTick,
            int packedLight, int packedOverlay,
            float red, float green, float blue, float alpha) {

        if (ManeDye.retiredOverlay(bone.getName())) return;
        if (gazeFrame != null && PonyArmorRenderer.hidesBone(animatable.getPlayer(), bone.getName())) return;
        ModelConfig config = getEffectiveConfig();
        if (!PonyFacePose.shouldRender(bone.getName(), config == null ? "01" : config.eyeStyle))
            return;

        boolean previousMirror = mirroredMane;
        boolean emote = animatable.isPlayingEmote();
        try (var mirror = ManeMirror.begin(poseStack, config, bone.getName());
                PonyFlightPose flight = PonyFlightPose.apply(bone, flightFrame, worldFlightRender() && !emote, isReRender);
                PonyWingFlightPose wing = PonyWingFlightPose.apply(bone,
                        worldFlightRender() && !emote && !isReRender ? wingFlightPose : null,
                        animatable.getPlayer() == null ? 0 : animatable.getPlayer().age + (double) partialTick);
                PonyEmotePose emotePose = PonyEmotePose.apply(bone, emote ? EmoteClient.action(animatable.getPlayer()) : "");
                HeadPose head = applyHeadLook(bone, animatable);
                PonyHeldItemPose holding = emote ? null
                        : PonyHeldItemPose.apply(bone, heldItems, heldWeights, isReRender);
                PonyFacePose face = "Emotions".equals(bone.getName())
                ? PonyFacePose.apply(bone) : null) {
            if (mirror != null) mirroredMane = !previousMirror;
            if (face != null) {
                String style = config == null ? "01" : config.eyeStyle;
                applyGaze(poseStack, bone, face, animatable, style);
            }
            String name = bone.getName().toLowerCase();
            // 翅膀使用身体图集，只有鬃毛和尾巴使用第二张贴图。
            boolean isOther = name.contains("mane") || name.contains("tail");
            Identifier texture = isOther ? PONY_TS : PONY_BASE;
            Identifier palette = isOther ? ManeTintTextures.get(config, bone.getName())
                    : EyeMaterials.isEyeBone(bone.getName()) ? EyeTintTextures.get(config, false) : null;
            if (palette == null && !isOther)
                palette = BodyTintTextures.get(config, BodyTintTextures.colorForBone(config, bone.getName()), bone.getName());
            if (palette != null) texture = palette;

            RenderLayer newRenderType = this.getRenderType(animatable, texture, bufferSource, partialTick);
            if (auraCapture != null) auraCapture.layers.add(newRenderType);
            VertexConsumer newBuffer = bufferSource.getBuffer(newRenderType);
            if (bodyAuraCapture != null && !isReRender) newBuffer = bodyAuraCapture.wrap(newBuffer, texture);
            boolean previousPalette = usingPalette;
            VertexConsumerProvider previousBuffers = eyeBuffers;
            RenderLayer previousEye = eyeLayer, previousPupil = pupilLayer;
            EyeApertureRender previousAperture = eyeAperture;
            eyeAperture = EyeApertureRender.begin(poseStack, bone);
            eyeBuffers = null;
            if (EyeMaterials.isEyeBone(bone.getName())) {
                Identifier pupil = EyeTintTextures.get(config, true);
                eyeBuffers = bufferSource;
                eyeLayer = newRenderType;
                pupilLayer = getRenderType(animatable, pupil == null ? PONY_BASE : pupil, bufferSource, partialTick);
                if (auraCapture != null) auraCapture.layers.add(pupilLayer);
            }
            usingPalette = palette != null;
            try {
                super.renderRecursively(poseStack, animatable, bone, newRenderType, bufferSource, newBuffer, isReRender,
                        partialTick, packedLight, packedOverlay, red, green, blue, alpha);
                if (!isReRender && gazeFrame != null && "Head".equals(bone.getName()) && !bone.isHidden()) {
                    poseStack.push();
                    try {
                        RenderUtils.prepMatrixForBone(poseStack, bone);
                        magicMouthFrame = new Matrix4f(poseStack.peek().getPositionMatrix());
                    } finally { poseStack.pop(); }
                }
                if (!isReRender) PonyHeldItems.renderAtBone(poseStack, bone, bufferSource, heldItems, packedLight, packedOverlay);
                if (!isReRender && gazeFrame != null && bodyVisibility == PonyVisibility.VISIBLE)
                    PonyArmorRenderer.renderAtBone(this, poseStack, bone, bufferSource, animatable.getPlayer(),
                            packedLight, packedOverlay, bodyAuraCapture);
                if (!isReRender && bodyAuraCapture != null && "Body".equals(bone.getName()) && !bone.isHidden()) {
                    var player = animatable.getPlayer();
                    MatrixStack auraPose = new MatrixStack();
                    auraPose.peek().getPositionMatrix().set(poseStack.peek().getPositionMatrix());
                    auraPose.peek().getNormalMatrix().set(poseStack.peek().getNormalMatrix());
                    RenderUtils.prepMatrixForBone(auraPose, bone);
                    float strength = flightFrame.magic() * PonyFlightVisuals.auraBrightness(player, partialTick);
                    auraCapture.draws.add(BodyMagicStars.capture(auraPose, bone, GlowingItem.getGlowColor(config),
                            player.age + (double) partialTick, player.getUuid().hashCode(), strength));
                }
            } finally {
                usingPalette = previousPalette;
                eyeBuffers = previousBuffers;
                eyeLayer = previousEye;
                pupilLayer = previousPupil;
                eyeAperture = previousAperture;
            }
        } finally { mirroredMane = previousMirror; }
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f matrix, Vector3f normal, VertexConsumer buffer,
            int light, int overlay, float red, float green, float blue, float alpha) {
        if (mirroredMane) ManeMirror.emitReversed(quad, matrix, normal, buffer, light, overlay, red, green, blue, alpha);
        else super.createVerticesOfQuad(quad, matrix, normal, buffer, light, overlay, red, green, blue, alpha);
    }

    private HeadPose applyHeadLook(GeoBone bone, GeckoPlayerAnimatable animatable) {
        // 仅世界渲染注入设置此frame，主预览和静态缩略图都不跟随玩家视角。
        if (gazeFrame == null || headLookActive || animatable.isPlayingEmote()) return null;
        boolean neck = "Neck".equals(bone.getName());
        if (!neck && !"Head".equals(bone.getName())) return null;
        var player = animatable.getPlayer();
        if (player == null || !player.isAlive()) return null;
        ModelConfig config = getEffectiveConfig();
        PonyHeadLookMath.Pose pose = player.isSleeping() ? PonyHeadLookMath.Pose.SLEEPING
                : player.isFallFlying() || player.isUsingRiptide()
                        || (config != null && config.showWings && player.getAbilities().flying && player.isSprinting())
                        ? PonyHeadLookMath.Pose.FLYING
                : player.isSwimming() || player.getLeaningPitch(gazePartialTick) > .01f
                        ? PonyHeadLookMath.Pose.SWIMMING : PonyHeadLookMath.Pose.NORMAL;
        float previousBodyYaw = player.prevBodyYaw, bodyYaw = player.bodyYaw;
        if (PonyBodyYaw.hasLivingMount(player)) previousBodyYaw = bodyYaw = PonyBodyYaw.sample(player, gazePartialTick);
        var rotation = PonyHeadLookMath.sample(previousBodyYaw, bodyYaw, player.prevHeadYaw, player.headYaw,
                player.prevPitch, player.getPitch(), gazePartialTick, pose);
        if (wingFlightPose != null && worldFlightRender()) {
            var look = player.getRotationVec(gazePartialTick);
            rotation = PonyHeadLookMath.flight(PonyWingFlightMath.rotation(wingFlightPose),
                    (float) look.x, (float) look.y, (float) look.z, 1 - PonyWingFlightMath.curl(wingFlightPose));
        }
        if (neck) {
            GeoBone head = bone.getChildBones().stream().filter(child -> "Head".equals(child.getName())).findFirst().orElse(null);
            if (head == null) return null;
            var target = backwardHeadPose.sample(player, bone, head, animatable.backwardLook(gazePartialTick), rotation);
            return target == null ? null : new HeadPose(this, bone, head, target);
        }
        if (rotation.pitch() == 0 && rotation.yaw() == 0) return null;
        return new HeadPose(this, bone, rotation);
    }

    static final class HeadPose implements AutoCloseable {
        private final PonyRenderer renderer;
        private final SavedBone first;
        private SavedBone second;
        private boolean closed;

        HeadPose(PonyRenderer renderer, GeoBone bone, PonyHeadLookMath.Rotation rotation) {
            this.renderer = renderer;
            first = new SavedBone(bone);
            bone.updateRotation(first.x + rotation.pitch(), first.y + rotation.yaw(), first.z);
            if (renderer != null) renderer.headLookActive = true;
        }

        HeadPose(PonyRenderer renderer, GeoBone neck, GeoBone head, PonyBackwardHeadPose.Rotations rotations) {
            this.renderer = renderer;
            first = new SavedBone(neck);
            second = new SavedBone(head);
            var n = rotations.neck(); var h = rotations.head();
            neck.updateRotation(n.x(), n.y(), n.z());
            head.updateRotation(h.x(), h.y(), h.z());
            if (renderer != null) renderer.headLookActive = true;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                first.restore();
                if (second != null) second.restore();
            } finally {
                if (renderer != null) renderer.headLookActive = false;
            }
        }

        private static final class SavedBone {
            final GeoBone bone;
            final float x, y, z;
            final boolean rotationChanged, positionChanged, scaleChanged;
            SavedBone(GeoBone bone) {
                this.bone = bone;
                x = bone.getRotX(); y = bone.getRotY(); z = bone.getRotZ();
                rotationChanged = bone.hasRotationChanged();
                positionChanged = bone.hasPositionChanged();
                scaleChanged = bone.hasScaleChanged();
            }
            void restore() {
                bone.updateRotation(x, y, z);
                bone.resetStateChanges();
                if (rotationChanged) bone.markRotationAsChanged();
                if (positionChanged) bone.markPositionAsChanged();
                if (scaleChanged) bone.markScaleAsChanged();
            }
        }
    }

    private void applyGaze(MatrixStack stack, GeoBone root, PonyFacePose face, GeckoPlayerAnimatable animatable, String style) {
        var player = animatable.getPlayer();
        var gui = PonyGuiGaze.current();
        if (gui != null) {
            if (gui.entity() == player && animatable.allowsAutomaticGaze() && face.allowsGaze())
                applyGuiGaze(stack, root, face, style, gui);
            return;
        }
        var forced=player==null?null: AppearanceOverrides.gazeTarget(player.getUuid());
        if (gazeFrame == null || (forced==null && !Config.getInstance().automaticGaze) || !animatable.allowsAutomaticGaze()
                || player == null || !player.isAlive() || player.isSleeping() || !face.allowsGaze()) {
            gazeSmoother.reset();
            turnGaze.reset();
            return;
        }
        GeoBone left = face.pupil(style, true);
        GeoBone right = face.pupil(style, false);
        if (left == null || right == null || left.getParent() != right.getParent()) {
            gazeSmoother.reset();
            turnGaze.reset();
            return;
        }
        net.minecraft.entity.Entity target = forced!=null?forced:ClientGaze.targetFor(player);
        double ticks = (double) player.age + gazePartialTick;
        float viewYaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(gazePartialTick, player.prevHeadYaw, player.headYaw);
        float viewPitch = net.minecraft.util.math.MathHelper.lerp(gazePartialTick, player.prevPitch, player.getPitch());
        var turn = turnGaze.sample(player, player.getWorld(), ticks, viewYaw, viewPitch,
                player.getX(), player.getY(), player.getZ(), target != null);
        if (turn.resetSmoothing()) gazeSmoother.reset();
        PonyGazeMath.Offset desired = turn.offset();
        if (target != null) {
            Vec3d relative = target.getLerpedPos(gazePartialTick).add(0, target.getEyeHeight(target.getPose()), 0)
                    .subtract(player.getLerpedPos(gazePartialTick));
            Vector3f targetInRender = gazeFrame.transformPosition(new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
            var path = new java.util.ArrayDeque<GeoBone>();
            for (GeoBone bone = left.getParent(); bone != null; bone = bone.getParent()) {
                path.addFirst(bone);
                if (bone == root) break;
            }
            if (path.peekFirst() != root) return;
            stack.push();
            try {
                for (GeoBone bone : path) {
                    // 眨眼隐藏父骨骼时保留已平滑的注视，不求逆退化矩阵。
                    if (Math.abs(bone.getScaleX() * bone.getScaleY() * bone.getScaleZ()) < 0.001f) return;
                    RenderUtils.prepMatrixForBone(stack, bone);
                }
                Vector3f center = new Vector3f(
                        (left.getPivotX() + right.getPivotX() - left.getPosX() - right.getPosX()) / 32f,
                        (left.getPivotY() + right.getPivotY() + left.getPosY() + right.getPosY()) / 32f,
                        (left.getPivotZ() + right.getPivotZ() + left.getPosZ() + right.getPosZ()) / 32f);
                desired = PonyGazeMath.project(stack.peek().getPositionMatrix(), targetInRender, center);
            } finally {
                stack.pop();
            }
        }
        var offset = gazeSmoother.step(desired, ticks);
        face.gaze(style, offset.x(), offset.y());
    }

    private void applyGuiGaze(MatrixStack stack, GeoBone root, PonyFacePose face, String style, PonyGuiGaze.Frame gui) {
        GeoBone left = face.pupil(style, true), right = face.pupil(style, false);
        if (left == null || right == null || left.getParent() != right.getParent()) return;
        var path = new java.util.ArrayDeque<GeoBone>();
        for (GeoBone bone = left.getParent(); bone != null; bone = bone.getParent()) {
            path.addFirst(bone);
            if (bone == root) break;
        }
        if (path.peekFirst() != root) return;
        stack.push();
        try {
            for (GeoBone bone : path) {
                if (Math.abs(bone.getScaleX() * bone.getScaleY() * bone.getScaleZ()) < .001f) return;
                RenderUtils.prepMatrixForBone(stack, bone);
            }
            Vector3f center = new Vector3f(
                    (left.getPivotX() + right.getPivotX() - left.getPosX() - right.getPosX()) / 32f,
                    (left.getPivotY() + right.getPivotY() + left.getPosY() + right.getPosY()) / 32f,
                    (left.getPivotZ() + right.getPivotZ() + left.getPosZ() + right.getPosZ()) / 32f);
            var desired = PonyGuiGaze.project(gui, stack.peek().getPositionMatrix(),
                    com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),
                    com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(), center);
            var offset = guiGaze.step(gui, desired);
            face.gaze(style, offset.x(), offset.y());
        } finally {
            stack.pop();
        }
    }

    @Override
    public void applyRenderLayersForBone(MatrixStack stack, GeckoPlayerAnimatable animatable, GeoBone bone,
            RenderLayer renderType, VertexConsumerProvider buffers, VertexConsumer buffer,
            float partialTick, int light, int overlay) {
        super.applyRenderLayersForBone(stack, animatable, bone, renderType, buffers, buffer, partialTick, light, overlay);
        if (!"Horn".equalsIgnoreCase(bone.getName()) || bone.isHidden() || auraCapture == null) return;
        ModelConfig config = getEffectiveConfig();
        var player = animatable.getPlayer();
        if ((config != null && !config.showHorn) || player == null || player.isInvisible() || player.isSpectator()
                || !player.isAlive()) return;
        boolean worldHorn = gazeFrame != null && HornAuraPass.isWorld();
        float progress = worldHorn ? Math.max(MagicEquip.hornProgress(player, partialTick), worldFlightRender() ? flightFrame.amount() : 0)
                : player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty() ? 0 : 1;
        if (progress <= 0) return;
        int ignition = Math.round(net.minecraft.util.math.MathHelper.clamp(progress, 0, 1) * 32767);

        int color = GlowingItem.getGlowColor(config);
        float cr = (color >>> 16 & 255) / 255f, cg = (color >>> 8 & 255) / 255f, cb = (color & 255) / 255f;
        int clock = Math.round((Math.floorMod(player.age, 240) + partialTick) * 50);
        double ticks = (double) player.age + partialTick;
        int seed = player.getUuid().hashCode();
        MatrixStack pose = new MatrixStack();
        pose.peek().getPositionMatrix().set(stack.peek().getPositionMatrix());
        pose.peek().getNormalMatrix().set(stack.peek().getNormalMatrix());
        var cubes = java.util.List.copyOf(bone.getCubes());
        auraCapture.draws.add(glow -> {
            for (var cube : cubes) {
                int layer = 0;
                for (var shell : hornGlow.shells(cube)) {
                    pose.push();
                    try {
                        renderCube(pose, shell, glow, ignition, clock | 4 << 16, cr, cg, cb, 0.26f - layer++ * 0.09f);
                    } finally {
                        pose.pop();
                    }
                }
                renderMagicStars(pose, cube, glow, ticks, seed, cr, cg, cb, clock, progress);
            }
        });
    }

    private void renderMagicStars(MatrixStack stack, software.bernie.geckolib.cache.object.GeoCube cube,
            VertexConsumer buffer, double ticks, int seed, float red, float green, float blue, int clock, float progress) {
        stack.push();
        try {
            RenderUtils.translateToPivotPoint(stack, cube);
            RenderUtils.rotateMatrixAroundCube(stack, cube);
            RenderUtils.translateAwayFromPivotPoint(stack, cube);
            Vector3f[] bounds = HornGlowGeometry.bounds(cube);
            Vector3f center = bounds[0].lerp(bounds[1], 0.5f, new Vector3f());
            center.y = bounds[0].y * 0.25f + bounds[1].y * 0.75f;
            Matrix4f inverseView = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).invert();
            Vector3f right = inverseView.transformDirection(new Vector3f(1, 0, 0)).normalize();
            Vector3f up = inverseView.transformDirection(new Vector3f(0, 1, 0)).normalize();
            Vector3f normal = inverseView.transformDirection(new Vector3f(0, 0, 1)).normalize();
            for (int slot = 0; slot < 4; slot++) {
                var star = MagicSparkles.sample(ticks, seed, slot);
                if (star == null) continue;
                float height = net.minecraft.util.math.MathHelper.clamp((center.y + star.y() - bounds[0].y)
                        / Math.max(bounds[1].y - bounds[0].y, .0001f), 0, 1);
                float ignition = net.minecraft.util.math.MathHelper.clamp((progress * 1.16f - height) / .16f, 0, 1);
                float opacity = star.alpha() * ignition * ignition * (3 - 2 * ignition);
                if (opacity <= 0) continue;
                Vector3f position = stack.peek().getPositionMatrix().transformPosition(new Vector3f(center).add(star.x(), star.y(), star.z()));
                for (int corner = 0; corner < 4; corner++) {
                    float x = corner == 0 || corner == 3 ? -1 : 1, y = corner < 2 ? -1 : 1;
                    Vector3f point = new Vector3f(position).fma(x * star.radius(), right).fma(y * star.radius(), up);
                    buffer.vertex(point.x, point.y, point.z).color(red, green, blue, opacity)
                            .texture((x + 1) * 0.5f, (y + 1) * 0.5f).overlay(clock, 1).light(0xF000F0)
                            .normal(normal.x, normal.y, normal.z).next();
                }
            }
        } finally {
            stack.pop();
        }
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone,
            VertexConsumer buffer, int packedLight, int packedOverlay,
            float red, float green, float blue, float alpha) {
        ModelConfig config = getEffectiveConfig();
        if (config == null) {
            if (eyeAperture == null)
                super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            else if (!bone.isHidden()) for (var cube : bone.getCubes()) {
                poseStack.push();
                try { eyeAperture.cube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha); }
                finally { poseStack.pop(); }
            }
            return;
        }

        if (!shouldRenderSelectedMane(bone.getName()))
            return;

        if (!config.showHorn && bone.getName().equalsIgnoreCase("Horn"))
            return;
        if (!config.showWings && bone.getName().toLowerCase().contains("wing"))
            return;

        String boneName = bone.getName();
        String colorField = BodyTintTextures.colorForBone(config, boneName);
        if (colorField == null) colorField = ManePalette.colorForBone(config, boneName);

        if (colorField != null && !usingPalette) {
            int color = parseHexColor(colorField);
            float cr = ((color >> 16) & 0xFF) / 255.0f;
            float cg = ((color >> 8) & 0xFF) / 255.0f;
            float cb = (color & 0xFF) / 255.0f;
            red *= cr;
            green *= cg;
            blue *= cb;
        }

        if (eyeBuffers != null && EyeMaterials.isEyeBone(boneName)) {
            if (bone.isHidden()) return;
            try {
                for (var cube : bone.getCubes()) {
                    VertexConsumer eyeBuffer = eyeBuffers.getBuffer(EyeMaterials.isPupil(cube) ? pupilLayer : eyeLayer);
                    poseStack.push();
                    try {
                        if (eyeAperture == null)
                            renderCube(poseStack, cube, eyeBuffer, packedLight, packedOverlay, red, green, blue, alpha);
                        else eyeAperture.cube(poseStack, cube, eyeBuffer, packedLight, packedOverlay, red, green, blue, alpha);
                    } finally {
                        poseStack.pop();
                    }
                }
            } finally {
                eyeBuffers.getBuffer(eyeLayer);
            }
        } else {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    protected int parseHexColor(String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private boolean shouldRenderSelectedMane(String boneName) {
        ModelConfig config = getEffectiveConfig();
        if (config == null)
            return true;

        if (boneName.toLowerCase().contains("tail")) {
            if (boneName.equalsIgnoreCase("Tail"))
                return true;
            return boneName.startsWith(bonePrefix(config.tailStyle, PonyStylePart.TAIL));
        }

        if (!boneName.toLowerCase().contains("mane"))
            return true;

        if (boneName.equals("Mane") || boneName.equals("FrontMane") || boneName.equals("BackMane"))
            return true;

        // 前发、后发独立选择；AJ 前发已使用自己的 Style05 骨骼。
        if (boneName.startsWith(bonePrefix(config.frontManeStyle, PonyStylePart.FRONT_MANE)))
            return true;
        return boneName.startsWith(bonePrefix(config.backManeStyle, PonyStylePart.BACK_MANE));
    }

    /** Builds the "Style{id}{PartSuffix}" bone-name prefix, e.g. "Style02FrontMane". */
    private static String bonePrefix(String styleId, PonyStylePart part) {
        return "Style" + styleId + part.boneSuffix;
    }

}
