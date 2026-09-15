package top.elysianherd.magicaland.client.gui.ponycustom;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.gl.GlBlendState;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** 离屏缩略图不能改变调用方的 framebuffer 或 Minecraft 状态缓存。 */
final class ThumbnailRenderState implements AutoCloseable {
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport = integers(GL11.GL_VIEWPORT);
    private final int[] scissorBox = integers(GL11.GL_SCISSOR_BOX);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final int[] colorMask = integers(GL11.GL_COLOR_WRITEMASK);
    private final float[] clearColor = floats(GL11.GL_COLOR_CLEAR_VALUE);
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int vertexBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int indexBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final VertexSorter sorting = RenderSystem.getVertexSorting();
    private final ShaderProgram shader = RenderSystem.getShader();
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final int[] textures;
    private final int[] shaderTextures;

    static ThumbnailRenderState capture(int textureCount) { return new ThumbnailRenderState(textureCount); }

    private ThumbnailRenderState(int textureCount) {
        textures = new int[textureCount];
        shaderTextures = new int[textureCount];
        for (int i = 0; i < textureCount; i++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            shaderTextures[i] = RenderSystem.getShaderTexture(i);
        }
        RenderSystem.activeTexture(activeTexture);
    }

    @Override
    public void close() {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (scissor) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
        // 同时恢复 shader 的混合状态缓存；仅调用 defaultBlendFunc 并不等价。
        new GlBlendState().enable();
        if (blend) new GlBlendState(srcRgb, dstRgb, srcAlpha, dstAlpha, equationRgb).enable();
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        RenderSystem.clearDepth(clearDepth);
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.setShaderTexture(i, shaderTextures[i]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(textures[i]);
        }
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        GlStateManager._glUseProgram(program);
        BufferRenderer.resetCurrentVertexBuffer();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        if (vao != 0) GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
    }

    private static int[] integers(int name) {
        int[] values = new int[4];
        GL11.glGetIntegerv(name, values);
        return values;
    }

    private static float[] floats(int name) {
        float[] values = new float[4];
        GL11.glGetFloatv(name, values);
        return values;
    }
}
