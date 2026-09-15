/*
 * 此处代码实现参考了 MineLittlePony (https://github.com/MineLittlePony/MineLittlePony) 的实现方式
 * The code implementation here references the implementation approach of MineLittlePony (https://github.com/MineLittlePony/MineLittlePony).
 *
 * ---
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Mine Little Pony
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package top.elysianherd.magicaland.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;

import com.mojang.blaze3d.systems.RenderSystem;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public abstract class MagicGlow extends RenderPhase {

    private static boolean worldPass;
    private static boolean initialized;
    private static net.minecraft.client.gl.ShaderProgram shader;
    private static final ShaderProgram MAGIC_PROGRAM = new ShaderProgram(() -> shader);
    private static net.minecraft.client.gl.ShaderProgram auraShader;
    private static final ShaderProgram AURA_PROGRAM = new ShaderProgram(() -> auraShader);
    private static final Transparency AURA_TRANSPARENCY = new Transparency("magicaland_aura", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE,
                org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    public static void init() {
        if (initialized) return;
        initialized = true;
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                new Identifier("magicaland", "magic_glow"),
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, loaded -> shader = loaded));
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                new Identifier("magicaland", "horn_aura"),
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, loaded -> auraShader = loaded));
        WorldRenderEvents.START.register(context -> worldPass = true);
        WorldRenderEvents.END.register(context -> worldPass = false);
        HornAuraPass.init();
        MagicFlame.init();
    }

    private MagicGlow() {
        super(null, null, null);
    }

    private record LayerKey(Identifier texture, int color, boolean world) {}
    private record AuraKey(Identifier texture, boolean fabulous) {}
    private static final Identifier AURA_DEFAULT = new Identifier("minecraft", "textures/atlas/blocks.png");
    private static final Cache<AuraKey, RenderLayer> AURA_LAYERS = CacheBuilder.newBuilder()
            .maximumSize(128).build();

    private static final Cache<LayerKey, RenderLayer> TINTED_LAYERS = CacheBuilder.newBuilder()
            .maximumSize(256).build();
    private static final Cache<LayerKey, RenderLayer> LEGACY_TINTED_LAYERS = CacheBuilder.newBuilder()
            .maximumSize(256).build();

    private static RenderLayer createTintedLayer(LayerKey key) {
        return RenderLayer.of(
                key.world() ? "mod_magic_world" : "mod_magic_preview",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                256,
                false,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new Colored(key.texture(), key.color()))
                        .program(MAGIC_PROGRAM)
                        // 保留全亮光晕；透明像素丢弃，实际深度参与后续合成。
                        .writeMaskState(ALL_MASK)
                        .depthTest(LEQUAL_DEPTH_TEST)
                        .target(key.world() ? ITEM_ENTITY_TARGET : MAIN_TARGET)
                        .transparency(LIGHTNING_TRANSPARENCY)
                        .lightmap(DISABLE_LIGHTMAP)
                        .cull(DISABLE_CULLING)
                        .build(false));
    }

    private static RenderLayer createAura(boolean fabulous, Identifier texture) {
        return RenderLayer.of(fabulous ? "mod_horn_aura_fabulous" : "mod_horn_aura_standard",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 32768, false, true,
                RenderLayer.MultiPhaseParameters.builder().program(AURA_PROGRAM).texture(new Texture(texture, false, false))
                        .writeMaskState(fabulous ? ALL_MASK : COLOR_MASK).depthTest(LEQUAL_DEPTH_TEST)
                        .target(fabulous ? ITEM_ENTITY_TARGET : MAIN_TARGET).transparency(AURA_TRANSPARENCY)
                        .lightmap(DISABLE_LIGHTMAP).cull(DISABLE_CULLING).build(false));
    }

    public static RenderLayer aura(boolean fabulous) {
        return aura(fabulous, AURA_DEFAULT);
    }

    static RenderLayer aura(boolean fabulous, Identifier texture) {
        AuraKey key = new AuraKey(texture == null ? AURA_DEFAULT : texture, fabulous);
        RenderLayer layer = AURA_LAYERS.getIfPresent(key);
        if (layer == null) {
            layer = createAura(fabulous, key.texture());
            AURA_LAYERS.put(key, layer);
        }
        return layer;
    }

    public static RenderLayer getColoured(Identifier texture, int color) {
        LayerKey key = new LayerKey(texture, color & 0xFFFFFF, worldPass);
        RenderLayer layer = TINTED_LAYERS.getIfPresent(key);
        if (layer == null) {
            layer = createTintedLayer(key);
            TINTED_LAYERS.put(key, layer);
        }
        return layer;
    }

    public static RenderLayer getLegacyColoured(Identifier texture, int color) {
        LayerKey key = new LayerKey(texture, color & 0xFFFFFF, false);
        RenderLayer layer = LEGACY_TINTED_LAYERS.getIfPresent(key);
        if (layer == null) {
            layer = RenderLayer.of(
                    "mod_magic_legacy",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS,
                    256,
                    true,
                    true,
                    RenderLayer.MultiPhaseParameters.builder()
                            .texture(new Colored(key.texture(), key.color()))
                            .program(EYES_PROGRAM)
                            .writeMaskState(COLOR_MASK)
                            .depthTest(LEQUAL_DEPTH_TEST)
                            .transparency(LIGHTNING_TRANSPARENCY)
                            .lightmap(DISABLE_LIGHTMAP)
                            .cull(DISABLE_CULLING)
                            .build(true));
            LEGACY_TINTED_LAYERS.put(key, layer);
        }
        return layer;
    }

    private static class Colored extends Texture {
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;

        public Colored(Identifier texture, int color) {
            super(texture, false, false);
            this.red = ((color >> 16) & 0xFF) / 255.0F;
            this.green = ((color >> 8) & 0xFF) / 255.0F;
            this.blue = (color & 0xFF) / 255.0F;
            this.alpha = 0.8F;
        }

        @Override
        public void startDrawing() {
            RenderSystem.setShaderColor(red, green, blue, alpha);
            super.startDrawing();
        }

        @Override
        public void endDrawing() {
            super.endDrawing();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }

    }
}
