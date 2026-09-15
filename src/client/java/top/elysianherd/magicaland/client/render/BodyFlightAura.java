package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBlendState;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** 整帧共用剪影；光晕只向独立目标写深度，不改身体深度。 */
public final class BodyFlightAura {
    static final int MAX_CAPTURES = 24, MAX_VERTICES = 65536, MAX_EDGE = 1024;
    private static final List<Capture> PENDING = new ArrayList<>();
    private static final BufferBuilder BUFFER = new BufferBuilder(65536);
    private static ShaderProgram maskShader, softenShader, compositeShader;
    private static Framebuffer mask, scene, glow;
    private static boolean initialized, accepting, disabled;
    private static int vertices;

    private BodyFlightAura() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            release();
            disabled = false;
            context.register(id("body_aura_mask"), VertexFormats.POSITION_COLOR_TEXTURE, shader -> maskShader = shader);
            context.register(id("body_aura_soften"), VertexFormats.POSITION_COLOR_TEXTURE, shader -> softenShader = shader);
            context.register(id("body_aura_composite"), VertexFormats.POSITION_COLOR_TEXTURE, shader -> compositeShader = shader);
        });
        WorldRenderEvents.START.register(context -> {
            clear();
            accepting = true;
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            accepting = false;
            if (context.advancedTranslucency()) drawWorld(true);
        });
        WorldRenderEvents.LAST.register(context -> {
            accepting = false;
            // 普通画质此时深度包含云层；前方云仍遮光，后方云不再覆盖光。
            if (!context.advancedTranslucency()) drawWorld(false);
        });
        WorldRenderEvents.END.register(context -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> release());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> release());
    }

    private static Identifier id(String name) { return new Identifier("magicaland", name); }

    private static void drawWorld(boolean fabulous) {
        try { if (!disabled) draw(fabulous); }
        catch (RuntimeException failure) {
            disabled = true;
            org.slf4j.LoggerFactory.getLogger("Magical-Land/BodyFlightAura")
                    .warn("飞行包身光已暂停，重新加载资源后重试", failure);
        }
        finally { clear(); }
    }

    /** 仅世界中可见的无翼独角兽调用；预览与第一人称不创建 capture。 */
    public static Capture begin(float progress, int color, double ticks) {
        if (disabled || !accepting || PENDING.size() >= MAX_CAPTURES || vertices >= MAX_VERTICES
                || !Float.isFinite(progress) || progress <= .001f || !Double.isFinite(ticks)) return null;
        return new Capture(Math.min(1, progress), color, (float) ((ticks % 24000) / 20));
    }

    public static final class Capture {
        private final Map<Identifier, List<Vertex>> batches = new LinkedHashMap<>();
        private final Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final float red, green, blue, progress, time;
        private boolean finished;

        private Capture(float progress, int color, float time) {
            red = (color >>> 16 & 255) / 255f;
            green = (color >>> 8 & 255) / 255f;
            blue = (color & 255) / 255f;
            this.progress = progress;
            this.time = time;
        }

        public VertexConsumer wrap(VertexConsumer original, Identifier texture) {
            if (finished || texture == null) return original;
            List<Vertex> target = batches.computeIfAbsent(texture, ignored -> new ArrayList<>());
            return new VertexConsumer() {
                private float x, y, z, u, v, alpha = 1;
                @Override public VertexConsumer vertex(double x, double y, double z) {
                    original.vertex(x, y, z);
                    this.x = (float) x; this.y = (float) y; this.z = (float) z;
                    return this;
                }
                @Override public VertexConsumer color(int r, int g, int b, int a) {
                    original.color(r, g, b, a); alpha = a / 255f; return this;
                }
                @Override public VertexConsumer texture(float u, float v) {
                    original.texture(u, v); this.u = u; this.v = v; return this;
                }
                @Override public VertexConsumer overlay(int u, int v) { original.overlay(u, v); return this; }
                @Override public VertexConsumer light(int u, int v) { original.light(u, v); return this; }
                @Override public VertexConsumer normal(float x, float y, float z) { original.normal(x, y, z); return this; }
                @Override public void fixedColor(int r, int g, int b, int a) {
                    original.fixedColor(r, g, b, a); alpha = a / 255f;
                }
                @Override public void unfixColor() { original.unfixColor(); alpha = 1; }
                @Override public void next() {
                    original.next();
                    if (finished || !accepting || vertices >= MAX_VERTICES) return;
                    target.add(new Vertex(x, y, z, u, v, alpha));
                    vertices++;
                }
            };
        }

        public void finish() {
            if (finished) return;
            finished = true;
            if (accepting && PENDING.size() < MAX_CAPTURES && !batches.isEmpty()) PENDING.add(this);
        }
    }

    private record Vertex(float x, float y, float z, float u, float v, float alpha) {
        boolean finite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    && Float.isFinite(u) && Float.isFinite(v) && Float.isFinite(alpha);
        }
    }

    private static void draw(boolean fabulous) {
        if (PENDING.isEmpty() || maskShader == null || softenShader == null || compositeShader == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Framebuffer target = fabulous ? client.worldRenderer.getEntityFramebuffer() : main;
        if (target == null || main.getDepthAttachment() < 0 || main.textureWidth < 1 || main.textureHeight < 1) return;
        // 非原版的多重采样目标交给兼容层，不能向不支持的 depth blit 继续提交。
        if (GL11.glGetInteger(GL13.GL_SAMPLES) > 0) return;
        try (State previous = new State()) {
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.clearDepth(1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            resize(main.textureWidth, main.textureHeight);
            scene.copyDepthFrom(main);
            mask.setClearColor(0, 0, 0, 0);
            mask.clear(MinecraftClient.IS_SYSTEM_MAC);
            mask.beginWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(1, scene.getDepthAttachment());
            maskShader.getUniformOrDefault("MaskSize").set((float) mask.textureWidth, (float) mask.textureHeight);
            RenderSystem.setShader(() -> maskShader);
            var viewStack = RenderSystem.getModelViewStack();
            for (Capture capture : PENDING) {
                viewStack.peek().getPositionMatrix().set(capture.view);
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(capture.projection, RenderSystem.getVertexSorting());
                for (var batch : capture.batches.entrySet()) {
                    RenderSystem.setShaderTexture(0, batch.getKey());
                    BUFFER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
                    List<Vertex> data = batch.getValue();
                    for (int index = 0; index + 3 < data.size(); index += 4) {
                        if (!data.get(index).finite() || !data.get(index + 1).finite()
                                || !data.get(index + 2).finite() || !data.get(index + 3).finite()) continue;
                        for (int corner = 0; corner < 4; corner++) {
                            Vertex vertex = data.get(index + corner);
                            BUFFER.vertex(vertex.x, vertex.y, vertex.z)
                                    .color(capture.red, capture.green, capture.blue, capture.progress * vertex.alpha)
                                    .texture(vertex.u, vertex.v).next();
                        }
                    }
                    submit();
                }
            }
            glow.setClearColor(0, 0, 0, 0);
            glow.clear(MinecraftClient.IS_SYSTEM_MAC);
            glow.beginWrite(true);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.setShaderTexture(0, mask.getColorAttachment());
            RenderSystem.setShaderTexture(1, mask.getDepthAttachment());
            RenderSystem.setShaderTexture(2, scene.getDepthAttachment());
            Capture first = PENDING.get(0);
            softenShader.getUniformOrDefault("MaskSize").set((float) mask.textureWidth, (float) mask.textureHeight);
            softenShader.getUniformOrDefault("ProjectionDepth").set(first.projection.m22(), first.projection.m32());
            softenShader.getUniformOrDefault("ProjectionScale").set(Math.abs(first.projection.m11()));
            softenShader.getUniformOrDefault("FlowTime").set(first.time);
            RenderSystem.setShader(() -> softenShader);
            screen();

            target.beginWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(fabulous);
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderTexture(0, glow.getColorAttachment());
            RenderSystem.setShaderTexture(1, glow.getDepthAttachment());
            RenderSystem.setShader(() -> compositeShader);
            screen();
        } finally {
            if (BUFFER.isBuilding()) {
                var unfinished = BUFFER.endNullable();
                if (unfinished != null) unfinished.release();
            }
            BUFFER.clear();
        }
    }

    private static void submit() {
        var built = BUFFER.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static void screen() {
        BUFFER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
        BUFFER.vertex(-1, -1, 0).color(255, 255, 255, 255).texture(0, 0).next();
        BUFFER.vertex(1, -1, 0).color(255, 255, 255, 255).texture(1, 0).next();
        BUFFER.vertex(1, 1, 0).color(255, 255, 255, 255).texture(1, 1).next();
        BUFFER.vertex(-1, 1, 0).color(255, 255, 255, 255).texture(0, 1).next();
        submit();
    }

    static int[] targetSize(int width, int height) {
        double scale = Math.min(1, MAX_EDGE / (double) Math.max(1, Math.max(width, height)));
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    private static void resize(int width, int height) {
        int[] size = targetSize(width, height);
        if (mask != null && mask.textureWidth == size[0] && mask.textureHeight == size[1]) return;
        deleteTargets();
        try {
            mask = new SimpleFramebuffer(size[0], size[1], true, MinecraftClient.IS_SYSTEM_MAC);
            scene = new SimpleFramebuffer(size[0], size[1], true, MinecraftClient.IS_SYSTEM_MAC);
            glow = new SimpleFramebuffer(size[0], size[1], true, MinecraftClient.IS_SYSTEM_MAC);
            mask.setTexFilter(GL11.GL_LINEAR);
            glow.setTexFilter(GL11.GL_LINEAR);
        } catch (RuntimeException | Error failure) {
            deleteTargets();
            throw failure;
        }
    }

    private static void clear() { PENDING.clear(); accepting = false; vertices = 0; }

    private static void deleteTargets() {
        if (mask != null) { mask.delete(); mask = null; }
        if (scene != null) { scene.delete(); scene = null; }
        if (glow != null) { glow.delete(); glow = null; }
    }

    private static void release() { clear(); deleteTargets(); }

    private static final class State implements AutoCloseable {
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int[] viewport = integers(GL11.GL_VIEWPORT), scissorBox = integers(GL11.GL_SCISSOR_BOX);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final GlBlendState blendState = GlBlendState.activeBlendState;
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int[] textures = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2)};
        private final int[] bindings = new int[3];
        private final ShaderProgram shader = RenderSystem.getShader();
        private final float[] color = RenderSystem.getShaderColor().clone();
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final com.mojang.blaze3d.systems.VertexSorter sorting = RenderSystem.getVertexSorting();
        private final boolean[] colorWrite = new boolean[4];

        State() {
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            var maskBits = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskBits);
            for (int i = 0; i < 4; i++) colorWrite[i] = maskBits.get(i) != 0;
            for (int i = 0; i < bindings.length; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.getModelViewStack().push();
        }

        @Override public void close() {
            RenderSystem.getModelViewStack().pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> shader);
            for (int i = 0; i < textures.length; i++) RenderSystem.setShaderTexture(i, textures[i]);
            GlStateManager._glUseProgram(program);
            for (int i = 0; i < bindings.length; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                RenderSystem.bindTexture(bindings[i]);
            }
            RenderSystem.activeTexture(activeTexture);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            else RenderSystem.disableScissor();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(depthWrite);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            RenderSystem.blendEquation(equation);
            if (alphaEquation != equation) GL20.glBlendEquationSeparate(equation, alphaEquation);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            // 着色器另有混色缓存；只还原 GL 会让原版暗角改成不透明覆盖。
            GlBlendState.activeBlendState = blendState;
            RenderSystem.colorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.clearDepth(clearDepth);
        }

        private static int[] integers(int key) { int[] value = new int[4]; GL11.glGetIntegerv(key, value); return value; }
    }
}
