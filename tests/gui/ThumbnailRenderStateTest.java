package top.elysianherd.magicaland.client.gui.ponycustom;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** 真实 Minecraft framebuffer + 隐藏 GLFW 上下文，不启动游戏。 */
public final class ThumbnailRenderStateTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(glfwInit(), "GLFW initialization");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(64, 64, "Thumbnail render state regression", 0, 0);
        check(window != 0, "hidden context");
        try (ZipFile zip = new ZipFile(args[0])) {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            SimpleFramebuffer destination = new SimpleFramebuffer(64, 64, true, false);
            SimpleFramebuffer readSource = new SimpleFramebuffer(32, 32, true, false);
            int[] textures = {GlStateManager._genTexture(), GlStateManager._genTexture(), GlStateManager._genTexture()};
            GlStateManager._glBindVertexArray(GlStateManager._glGenVertexArrays());
            GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, GlStateManager._glGenBuffers());
            GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GlStateManager._glGenBuffers());
            for (int i = 0; i < 3; i++) {
                RenderSystem.activeTexture(GL_TEXTURE0 + i);
                GlStateManager._bindTexture(textures[i]);
                RenderSystem.setShaderTexture(i, textures[2 - i]);
            }
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination.fbo);
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, readSource.fbo);
            RenderSystem.viewport(2, 3, 48, 51);
            RenderSystem.enableScissor(4, 5, 36, 39);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA, GL_ONE, GL_ZERO);
            glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.depthFunc(GL_GREATER);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, false, true, false);
            RenderSystem.clearColor(.1f, .2f, .3f, .4f);
            RenderSystem.clearDepth(.3);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 64, 64, 0, -100, 100), VertexSorter.BY_Z);
            RenderSystem.setShaderColor(.2f, .4f, .6f, .8f);
            List<String> expected = snapshot();
            try (var state = ThumbnailRenderState.capture(3)) {
                RenderSystem.disableScissor();
                RenderSystem.activeTexture(GL_TEXTURE0);
                SimpleFramebuffer temporary = new SimpleFramebuffer(16, 12, true, false);
                temporary.beginWrite(true);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                temporary.clear(false);
                temporary.delete();
                check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0, "actual old deletion bug reproduced inside guard");
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.setShaderTexture(1, 0);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            check(snapshot().equals(expected), "create/clear/delete exact GL and RenderSystem restore");

            Class<?> tileClass = Class.forName(PonyStyleThumbnails.class.getName() + "$Tile");
            Class<?> keyClass = Class.forName(PonyStyleThumbnails.class.getName() + "$Key");
            Constructor<?> tileCtor = tileClass.getDeclaredConstructors()[0], keyCtor = keyClass.getDeclaredConstructors()[0];
            tileCtor.setAccessible(true); keyCtor.setAccessible(true);
            Method cache = PonyStyleThumbnails.class.getDeclaredMethod("cache", keyClass, tileClass);
            cache.setAccessible(true);
            var cacheField = PonyStyleThumbnails.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            List<Integer> ids = new ArrayList<>();
            Object lastTile = null;
            SimpleFramebuffer lastFramebuffer = null;
            for (int i = 0; i < 85; i++) {
                SimpleFramebuffer tile;
                try (var state = ThumbnailRenderState.capture(3)) { tile = new SimpleFramebuffer(16, 12, true, false); }
                ids.add(tile.fbo);
                lastFramebuffer = tile;
                lastTile = tileCtor.newInstance(tile);
                cache.invoke(null, keyCtor.newInstance(PonyStylePart.FRONT_MANE, Integer.toString(i), 16, 12), lastTile);
                check(snapshot().equals(expected), "cache insert/eviction preserves GL state " + i);
                check(((Map<?, ?>) cacheField.get(null)).size() == Math.min(i + 1, 40), "bounded actual cache");
                if (i >= 40) check(!glIsFramebuffer(ids.get(i - 40)), "evicted GPU resource deleted");
            }
            ResourcePack pack = (ResourcePack) java.lang.reflect.Proxy.newProxyInstance(ResourcePack.class.getClassLoader(),
                    new Class<?>[] {ResourcePack.class}, (proxy, method, arguments) -> method.getName().equals("getName") ? "cached vanilla test shaders" : null);
            try (ShaderProgram shader = new ShaderProgram(id -> {
                var entry = zip.getEntry("assets/" + id.getNamespace() + "/" + id.getPath());
                return entry == null ? Optional.empty() : Optional.of(new Resource(pack, () -> zip.getInputStream(entry)));
            }, "position_tex", VertexFormats.POSITION_TEXTURE)) {
                var field = GameRenderer.class.getDeclaredField("positionTexProgram");
                field.setAccessible(true);
                field.set(null, shader);
                var context = new DrawContext(null, VertexConsumerProvider.immediate(new BufferBuilder(256)));
                Method blit = PonyStyleThumbnails.class.getDeclaredMethod("blit", DrawContext.class, tileClass,
                        int.class, int.class, int.class, int.class);
                blit.setAccessible(true);
                // draw() 自己会恢复 GUI depth test；以正常 flush 后状态作为调用边界。
                context.draw();
                expected = snapshot();
                blit.invoke(null, context, lastTile, 0, 0, 125, 49);
                check(snapshot().equals(expected), "actual textured blit exact state restore");
                var initialized = PonyStyleThumbnails.class.getDeclaredField("initialized");
                initialized.setAccessible(true);
                initialized.set(null, true);
                ModelConfig changingColors = new ModelConfig();
                for (int i = 0; i < 20; i++) {
                    changingColors.frontManeColor = String.format("#%06X", i * 711123);
                    changingColors.irisColor = String.format("#%06X", i * 55331);
                    changingColors.maneDyeEnabled = i % 2 == 0;
                    PonyStyleThumbnails.render(context, changingColors, PonyStylePart.FRONT_MANE, "84", 0, 0, 8, 6);
                    check(((Map<?, ?>) cacheField.get(null)).size() == 40, "color edits do not add cache entries");
                    check(snapshot().equals(expected), "color-edit cache hits preserve GL state");
                }
                try (var state = ThumbnailRenderState.capture(3)) {
                    RenderSystem.disableScissor();
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(true);
                    destination.setClearColor(0, 0, 1, 1);
                    destination.clear(false);
                    lastFramebuffer.setClearColor(.5f, 0, 0, .5f);
                    lastFramebuffer.clear(false);
                    destination.beginWrite(true);
                    GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, destination.fbo);
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                    RenderSystem.blendEquation(GL_FUNC_ADD);
                    RenderSystem.setShader(() -> shader);
                    RenderSystem.setShaderTexture(0, lastFramebuffer.getColorAttachment());
                    shader.bind();
                    blit.invoke(null, context, lastTile, 0, 0, 64, 48);
                    check(glGetInteger(GL_CURRENT_PROGRAM) == shader.getGlRef(), "caller actual program restored even when blit unbinds it");
                    float[] pixel = pixel();
                    check(close(pixel[0], .5f) && close(pixel[1], 0) && close(pixel[2], .5f), "premultiplied thumbnail blends without doubled alpha");
                    try (var inner = ThumbnailRenderState.capture(1)) {
                        lastFramebuffer.setClearColor(0, 1, 0, .5f);
                        lastFramebuffer.clear(false);
                    }
                    shader.bind();
                    check(glGetInteger(GL_CURRENT_PROGRAM) == shader.getGlRef(), "next same shader rebinds actual program");
                    check(glGetInteger(GL_BLEND_EQUATION_RGB) == GL_FUNC_ADD && glGetInteger(GL_BLEND_EQUATION_ALPHA) == GL_FUNC_ADD
                            && glGetInteger(GL_BLEND_SRC_RGB) == GL_SRC_ALPHA, "next same vanilla shader has correct blend state");
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableCull();
                    drawQuad();
                    shader.unbind();
                    pixel = pixel();
                    check(close(pixel[0], .25f) && close(pixel[1], .5f) && close(pixel[2], .25f), "next same shader really draws correct pixels");
                }
            }
            expected = snapshot();
            PonyStyleThumbnails.clear();
            check(snapshot().equals(expected), "cache clear restores draw/read targets and all bindings");
            check(((Map<?, ?>) cacheField.get(null)).isEmpty(), "cache fully cleared");
            for (int i = 45; i < ids.size(); i++) check(!glIsFramebuffer(ids.get(i)), "clear deletes remaining resource");
            Method create = PonyStyleThumbnails.class.getDeclaredMethod("create", ModelConfig.class, PonyStylePart.class, int.class, int.class);
            create.setAccessible(true);
            try { create.invoke(null, new ModelConfig(), PonyStylePart.FRONT_MANE, 16, 16); }
            catch (java.lang.reflect.InvocationTargetException failure) { check(failure.getCause() != null, "controlled missing-game create failure"); }
            check(snapshot().equals(expected), "failed create also restores full state");
            check(glGetError() == GL_NO_ERROR, "no OpenGL errors");
            System.out.println("PASS ThumbnailRenderStateTest: " + checks + " real framebuffer, eviction, clear, blit and GL-state checks; " + glGetString(GL_RENDERER));
        } finally { glfwDestroyWindow(window); glfwTerminate(); }
    }

    private static List<String> snapshot() {
        List<String> values = new ArrayList<>();
        for (int name : new int[] {GL_DRAW_FRAMEBUFFER_BINDING, GL_READ_FRAMEBUFFER_BINDING, GL_BLEND_SRC_RGB,
                GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA,
                GL_DEPTH_FUNC, GL_ACTIVE_TEXTURE, GL_CURRENT_PROGRAM, GL_VERTEX_ARRAY_BINDING,
                GL_ARRAY_BUFFER_BINDING, GL_ELEMENT_ARRAY_BUFFER_BINDING}) values.add(Integer.toString(glGetInteger(name)));
        for (int name : new int[] {GL_BLEND, GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST}) values.add(Boolean.toString(glIsEnabled(name)));
        values.add(Boolean.toString(glGetBoolean(GL_DEPTH_WRITEMASK)));
        for (int name : new int[] {GL_VIEWPORT, GL_SCISSOR_BOX, GL_COLOR_WRITEMASK}) { int[] v = new int[4]; glGetIntegerv(name, v); values.add(Arrays.toString(v)); }
        float[] clear = new float[4]; glGetFloatv(GL_COLOR_CLEAR_VALUE, clear); values.add(Arrays.toString(clear));
        values.add(Double.toString(glGetDouble(GL_DEPTH_CLEAR_VALUE)));
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 3; i++) {
            RenderSystem.activeTexture(GL_TEXTURE0 + i);
            values.add(Integer.toString(glGetInteger(GL_TEXTURE_BINDING_2D)));
            values.add(Integer.toString(RenderSystem.getShaderTexture(i)));
        }
        RenderSystem.activeTexture(active);
        values.add(Arrays.toString(RenderSystem.getShaderColor()));
        values.add(RenderSystem.getProjectionMatrix().toString());
        values.add(RenderSystem.getModelViewMatrix().toString());
        values.add(String.valueOf(RenderSystem.getShader()));
        return values;
    }
    private static float[] pixel() { float[] result = new float[4]; glReadPixels(32, 32, 1, 1, GL_RGBA, GL_FLOAT, result); return result; }
    private static boolean close(float first, float second) { return Math.abs(first - second) < .02f; }
    private static void drawQuad() {
        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(0, 48, 0).texture(0, 0).next();
        buffer.vertex(64, 48, 0).texture(1, 0).next();
        buffer.vertex(64, 0, 0).texture(1, 1).next();
        buffer.vertex(0, 0, 0).texture(0, 1).next();
        BufferRenderer.draw(buffer.end());
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
