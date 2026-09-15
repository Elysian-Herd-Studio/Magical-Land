package top.elysianherd.magicaland.client.gui.ponycustom;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimatableManager;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.model.GeckoPlayerAnimatable;
import top.elysianherd.magicaland.client.render.PonyRenderer;
import top.elysianherd.magicaland.client.render.ManeMirror;

/** 真实模型的静止试穿图；只在内存保留，最多每帧更新两张。 */
public final class PonyStyleThumbnails {
    private static final Logger LOGGER = LoggerFactory.getLogger(PonyStyleThumbnails.class);
    private static final int MAX_ENTRIES = 40;
    private static final int MAX_MODELS = 96;
    private static final Map<Key, Tile> CACHE = new LinkedHashMap<>(16, .75f, true);
    private static final Map<Key, Boolean> FAILED = new LinkedHashMap<>();
    private static final Map<ModelKey, Tile> MODELS = new LinkedHashMap<>(16, .75f, true);
    private static final Map<ModelKey, Boolean> FAILED_MODELS = new LinkedHashMap<>();
    private static boolean initialized;
    private static DrawContext lastContext;
    private static int remaining;
    private static FrozenRenderer renderer;
    private static final BufferBuilder IMAGE_BUFFER = new BufferBuilder(32768);
    private static final BufferBuilder BLIT_BUFFER = new BufferBuilder(256);
    private static final GeckoPlayerAnimatable DUMMY = new GeckoPlayerAnimatable() {
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    };

    private PonyStyleThumbnails() {}

    public static void render(DrawContext context, ModelConfig active, PonyStylePart part, String styleId,
            int x, int y, int width, int height) {
        if (active == null || part == null || width < 4 || height < 4) return;
        init();
        if (lastContext != context) { lastContext = context; remaining = 2; }
        ThumbnailSize size = ThumbnailSize.of(width, height);
        Key key = new Key(part, styleId, size.width(), size.height(), ManeMirror.enabled(active, part));
        Tile tile = CACHE.get(key);
        if (tile == null && remaining > 0 && !FAILED.containsKey(key)) {
            remaining--;
            try {
                context.draw();
                tile = create(previewConfig(active, part, styleId), part, size.width(), size.height());
                cache(key, tile);
            } catch (RuntimeException failure) {
                if (FAILED.size() >= MAX_ENTRIES) FAILED.remove(FAILED.keySet().iterator().next());
                FAILED.put(key, true);
                LOGGER.warn("Unable to render pony style thumbnail {} {}", part, styleId, failure);
            }
        }
        if (tile != null) blit(context, tile, x, y, width, height);
    }

    public static void renderModel(DrawContext context, ModelConfig snapshot, int x, int y, int width, int height) {
        if (snapshot == null || width < 4 || height < 4) return;
        init();
        if (lastContext != context) { lastContext = context; remaining = 2; }
        ThumbnailSize size = ThumbnailSize.of(width, height);
        ModelKey key = new ModelKey(snapshot, size.width(), size.height());
        Tile tile = MODELS.get(key);
        if (tile == null && remaining > 0 && !FAILED_MODELS.containsKey(key)) {
            remaining--;
            try {
                context.draw();
                tile = create(snapshot, null, size.width(), size.height());
                if (MODELS.size() >= MAX_MODELS) delete(MODELS.remove(MODELS.keySet().iterator().next()));
                MODELS.put(key, tile);
            } catch (RuntimeException failure) {
                if (FAILED_MODELS.size() >= MAX_MODELS) FAILED_MODELS.remove(FAILED_MODELS.keySet().iterator().next());
                FAILED_MODELS.put(key, true);
                LOGGER.warn("Unable to render pony model thumbnail {}", snapshot.name, failure);
            }
        }
        if (tile != null) blit(context, tile, x, y, width, height);
    }

    public static void clearModels() {
        for (Tile tile : MODELS.values()) delete(tile);
        MODELS.clear();
        FAILED_MODELS.clear();
    }

    public static void clear() {
        for (Tile tile : CACHE.values()) delete(tile);
        CACHE.clear();
        FAILED.clear();
        clearModels();
        lastContext = null;
        renderer = null;
        PreviewGeometryBounds.clear();
    }

    private static void cache(Key key, Tile tile) {
        if (CACHE.size() >= MAX_ENTRIES) {
            Key oldest = CACHE.keySet().iterator().next();
            delete(CACHE.remove(oldest));
        }
        CACHE.put(key, tile);
    }

    private static void delete(Tile tile) {
        try (var state = ThumbnailRenderState.capture(1)) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            tile.framebuffer.delete();
        }
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "style_thumbnails"); }
            @Override public void reload(ResourceManager manager) { clear(); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());
    }

    static ModelConfig previewConfig(ModelConfig source, PonyStylePart part, String styleId) {
        // 缩略图暂用固定示例色；玩家草稿只由左侧主预览实时显示。
        ModelConfig config = new ModelConfig();
        config.bodyShadingMode = "legacy";
        config.showHorn = false;
        config.showWings = false;
        config.frontManeColor = config.backManeColor = config.tailColor = "#75659C";
        config.maneColorLinkVersion = 1;
        config.maneDyeEnabled = false;
        ManeMirror.set(config, part, ManeMirror.enabled(source, part));
        switch (part) {
            case FRONT_MANE -> config.frontManeStyle = styleId;
            case BACK_MANE -> config.backManeStyle = styleId;
            case TAIL -> config.tailStyle = styleId;
            case EYE -> config.eyeStyle = styleId;
        }
        return ModelConfig.sanitize(config);
    }

    private static Tile create(ModelConfig config, PonyStylePart part, int width, int height) {
        var view = RenderSystem.getModelViewStack();
        SimpleFramebuffer framebuffer = null;
        boolean success = false;
        try (var state = ThumbnailRenderState.capture(3)) {
            view.push();
            try {
                RenderSystem.disableScissor();
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                framebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                framebuffer.setClearColor(0, 0, 0, 0);
                framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
                framebuffer.beginWrite(true);
                view.peek().getPositionMatrix().identity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000), VertexSorter.BY_Z);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                if (renderer == null) renderer = new FrozenRenderer();
                var bounds = PreviewGeometryBounds.framingBounds(config, part);
                MatrixStack stack = frame(bounds, part, width, height);
                var buffers = VertexConsumerProvider.immediate(IMAGE_BUFFER);
                renderer.draw(stack, config, part, buffers);
                buffers.draw();
                success = true;
                return new Tile(framebuffer);
            } finally {
                try {
                    if (IMAGE_BUFFER.isBuilding()) {
                        var unfinished = IMAGE_BUFFER.endNullable();
                        if (unfinished != null) unfinished.release();
                    }
                    IMAGE_BUFFER.clear();
                    if (!success && framebuffer != null) framebuffer.delete();
                } finally {
                    view.pop();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        }
    }

    private static MatrixStack frame(PreviewGeometryBounds.Bounds bounds, PonyStylePart part, int width, int height) {
        float yaw = part == PonyStylePart.BACK_MANE || part == PonyStylePart.TAIL ? 35 : 155;
        Matrix4f rotation = new Matrix4f().rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(-8));
        Vector3f center = bounds.center();
        float projectedWidth = 0, projectedHeight = 0;
        for (float x : new float[] {bounds.minX(), bounds.maxX()})
            for (float y : new float[] {bounds.minY(), bounds.maxY()})
                for (float z : new float[] {bounds.minZ(), bounds.maxZ()}) {
                    Vector3f point = rotation.transformPosition(new Vector3f(x, y, z).sub(center));
                    projectedWidth = Math.max(projectedWidth, Math.abs(point.x) * 2);
                    projectedHeight = Math.max(projectedHeight, Math.abs(point.y) * 2);
                }
        float scale = .89f * Math.min(width / Math.max(.1f, projectedWidth), height / Math.max(.1f, projectedHeight));
        MatrixStack stack = new MatrixStack();
        stack.translate(width / 2f, height / 2f, 0);
        stack.scale(scale, scale, scale);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8));
        stack.translate(-center.x, -center.y, -center.z);
        return stack;
    }

    private static void blit(DrawContext context, Tile tile, int x, int y, int width, int height) {
        context.draw();
        try (var state = ThumbnailRenderState.capture(1)) {
            try {
                var area = new ThumbnailSize(tile.framebuffer.textureWidth, tile.framebuffer.textureHeight).fit(width, height);
                float left = x + area.x(), top = y + area.y();
                var shader = GameRenderer.getPositionTexProgram();
                shader.addSampler("Sampler0", tile.framebuffer.getColorAttachment());
                if (shader.modelViewMat != null) shader.modelViewMat.set(RenderSystem.getModelViewMatrix());
                if (shader.projectionMat != null) shader.projectionMat.set(RenderSystem.getProjectionMatrix());
                if (shader.colorModulator != null) shader.colorModulator.set(1f, 1f, 1f, 1f);
                shader.bind();
                try {
                    RenderSystem.enableBlend();
                    RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                    RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                    var builder = BLIT_BUFFER;
                    builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    var matrix = context.getMatrices().peek().getPositionMatrix();
                    builder.vertex(matrix, left, top + area.height(), 0).texture(0, 0).next();
                    builder.vertex(matrix, left + area.width(), top + area.height(), 0).texture(1, 0).next();
                    builder.vertex(matrix, left + area.width(), top, 0).texture(1, 1).next();
                    builder.vertex(matrix, left, top, 0).texture(0, 1).next();
                    BufferRenderer.draw(builder.end());
                } finally { shader.unbind(); }
            } finally {
                if (BLIT_BUFFER.isBuilding()) {
                    var unfinished = BLIT_BUFFER.endNullable();
                    if (unfinished != null) unfinished.release();
                }
                BLIT_BUFFER.clear();
            }
        }
    }

    private record Key(PonyStylePart part, String style, int width, int height, boolean mirrored) {}
    private record ModelKey(ModelConfig snapshot, int width, int height) {}
    private record Tile(SimpleFramebuffer framebuffer) {}

    private static final class FrozenRenderer extends PonyRenderer {
        private ModelConfig selected;
        private PonyStylePart part;

        void draw(MatrixStack stack, ModelConfig config, PonyStylePart target, VertexConsumerProvider buffers) {
            selected = config;
            part = target;
            setOverrideConfig(config);
            try {
                RenderLayer layer = getRenderType(DUMMY, new Identifier("magicaland", "textures/entity/base.png"), buffers, 0);
                for (GeoBone root : PreviewGeometryBounds.model().topLevelBones())
                    renderRecursively(stack, DUMMY, root, layer, buffers, buffers.getBuffer(layer), true, 0,
                            0xF000F0, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);
            } finally { clearOverride(); selected = null; part = null; }
        }

        @Override
        public void renderCubesOfBone(MatrixStack stack, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            if (PreviewGeometryBounds.visible(bone, selected, part))
                super.renderCubesOfBone(stack, bone, buffer, light, overlay, red, green, blue, alpha);
        }

        @Override
        public RenderLayer getRenderType(GeckoPlayerAnimatable animatable, Identifier texture, VertexConsumerProvider buffers, float tick) {
            return RenderLayer.getEntityTranslucent(texture);
        }
    }
}
