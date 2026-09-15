package top.elysianherd.magicaland.client.gui.ponycustom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.loading.FileLoader;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.elysianherd.magicaland.client.model.GeckoPlayerAnimatable;
import top.elysianherd.magicaland.client.model.GeckoPlayerModel;
import top.elysianherd.magicaland.client.model.PonyPreviewAnimatable;
import top.elysianherd.magicaland.client.render.PonyGuiGaze;
import top.elysianherd.magicaland.client.render.PonyRenderer;

public final class PonyPreviewRenderer extends PonyRenderer implements AutoCloseable {
    private final PonyPreviewAnimatable ponyAnimatable = new PonyPreviewAnimatable();

    public PonyPreviewRenderer() { super(new PreviewModel()); }

    public void renderPreview(DrawContext context, ModelConfig config, Rect area, PreviewCamera.Pose pose,
            long now, float delta, int mouseX, int mouseY) {
        context.draw();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        context.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        var matrices = context.getMatrices();
        matrices.push();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            PreviewLightingRig.apply();
            ponyAnimatable.beginFrame(now);
            ponyAnimatable.setPlayer(MinecraftClient.getInstance().player);
            setOverrideConfig(config);
            matrices.translate(area.x() + area.width() * .5f, area.y() + area.height() * .5f, 150);
            matrices.scale(pose.scale(), pose.scale(), pose.scale());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.yaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.pitch()));
            matrices.translate(-pose.x() - .5f, -pose.y() - .51f, -pose.z() - .5f);
            RenderLayer layer = getRenderType(ponyAnimatable, getTextureLocation(ponyAnimatable),
                    context.getVertexConsumers(), delta);
            if (layer != null) {
                var consumer = context.getVertexConsumers().getBuffer(layer);
                var window = MinecraftClient.getInstance().getWindow();
                try (var gaze = PonyGuiGaze.begin(this, ponyAnimatable.getPlayer(), mouseX, mouseY,
                        window.getScaledWidth(), window.getScaledHeight())) {
                    render(matrices, ponyAnimatable, context.getVertexConsumers(), layer, consumer, 0xF000F0);
                }
            }
        } finally {
            try {
                context.draw();
            } finally {
                clearOverride();
                matrices.pop();
                context.disableScissor();
                DiffuseLighting.enableGuiDepthLighting();
                RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            }
        }
    }

    @Override
    public RenderLayer getRenderType(GeckoPlayerAnimatable animatable, Identifier texture,
            VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void close() {
        clearOverride();
        ponyAnimatable.reset();
    }

    private static final class PreviewModel extends GeckoPlayerModel {
        private BakedGeoModel sharedSource, previewModel;

        @Override
        public BakedGeoModel getBakedModel(Identifier location) {
            BakedGeoModel source = super.getBakedModel(location);
            if (source != sharedSource) {
                var raw = FileLoader.loadModelFile(location, MinecraftClient.getInstance().getResourceManager());
                previewModel = BakedModelFactory.getForNamespace(location.getNamespace())
                        .constructGeoModel(GeometryTree.fromModel(raw));
                // 每个预览只修改自己的动画骨骼。
                getAnimationProcessor().setActiveModel(previewModel);
                sharedSource = source;
            }
            return previewModel;
        }

        @Override
        public void handleAnimations(GeckoPlayerAnimatable animatable, long instanceId,
                AnimationState<GeckoPlayerAnimatable> state) {
            ((PonyPreviewAnimatable) animatable).prepareAnimationFrame(instanceId, state);
            super.handleAnimations(animatable, instanceId, state);
        }

        @Override
        public void applyMolangQueries(GeckoPlayerAnimatable animatable, double animTime) {
            if (MinecraftClient.getInstance().world == null) return;
            super.applyMolangQueries(animatable, animTime);
        }
    }
}
