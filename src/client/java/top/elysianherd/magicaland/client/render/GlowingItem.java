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

import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.client.render.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.world.World;

import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.util.RenderLayerHelper;

public class GlowingItem {

    public static void initLevitation() { ItemLevitation.init(); }

    /**
     * 世界手持物只读取已应用外观，不读取捏脸草稿。
     */
    public static int getCurrentGlowColor() {
        return getGlowColor(ModelManager.getAppliedModel());
    }

    public static int getGlowColor(ModelConfig config) {
        if (config == null) return 0xAA00FF;
        String hex = config.magicGlowColor;
        if (hex == null) return 0xAA00FF;
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() > 6) hex = hex.substring(hex.length() - 6);
        try {
            return Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return 0xAA00FF;
        }
    }

    public static void beginFirstPersonPass() {
        HornAuraPass.beginHands();
    }

    public static void endFirstPersonPass(VertexConsumerProvider.Immediate buffers) {
        try {
            buffers.draw();
            HornAuraPass.endHands();
        } finally {
            HornAuraPass.discardHands();
        }
    }

    public static void renderPreviewWithGlow(ItemRenderer renderer, ItemStack stack, ModelTransformationMode mode,
            MatrixStack matrices, VertexConsumerProvider buffers, @Nullable World world,
            int light, int seed, int glowColor) {
        if (isLegacyStyle()) {
            renderLegacy(renderer, null, stack, mode, false, matrices, buffers, world, light, seed, glowColor);
            return;
        }
        renderCaptured(renderer, null, stack, mode, false, matrices, buffers, world, light, seed, glowColor, false, LevitationTrail.EMPTY);
    }

    public void renderItemWithGlow(
            ItemRenderer itemRenderer,
            @Nullable LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode mode,
            boolean left,
            MatrixStack matrices,
            VertexConsumerProvider renderContext,
            @Nullable World world,
            int lightUv,
            int seed,
            int glowColor,
            boolean renderGlow) {
        renderItemWithGlow(itemRenderer, entity, stack, mode, left, matrices, renderContext, world,
                lightUv, seed, glowColor, renderGlow, LevitationTrail.EMPTY);
    }

    public void renderItemWithGlow(ItemRenderer itemRenderer, @Nullable LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean left, MatrixStack matrices, VertexConsumerProvider renderContext,
            @Nullable World world, int lightUv, int seed, int glowColor, boolean renderGlow, LevitationTrail trail) {

        boolean shouldRenderGlow = renderGlow && (mode.isFirstPerson()
                || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);

        if (shouldRenderGlow && !stack.isEmpty() && !isLegacyStyle()) {
            renderCaptured(itemRenderer, entity, stack, mode, left, matrices, renderContext, world,
                    lightUv, seed, glowColor, mode.isFirstPerson(), trail);
        } else if (shouldRenderGlow && !stack.isEmpty()) {
            renderLegacy(itemRenderer, entity, stack, mode, left, matrices, renderContext, world,
                    lightUv, seed, glowColor);
        } else {
            itemRenderer.renderItem(
                    entity, stack, mode, left,
                    matrices, renderContext, world,
                    lightUv, OverlayTexture.DEFAULT_UV, seed);
        }
    }

    private static void renderLegacy(ItemRenderer renderer, @Nullable LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean left, MatrixStack matrices, VertexConsumerProvider buffers,
            @Nullable World world, int light, int seed, int glowColor) {
        matrices.push();
        try {
            renderer.renderItem(entity, stack, mode, left, matrices, buffers, world, light,
                    OverlayTexture.DEFAULT_UV, seed);
            VertexConsumerProvider glow = layer -> {
                if (layer.getVertexFormat() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
                    return buffers.getBuffer(layer);
                return buffers.getBuffer(MagicGlow.getLegacyColoured(
                        RenderLayerHelper.getTexture(layer).orElse(net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE),
                        glowColor));
            };
            matrices.scale(1.1F, 1.1F, 1.1F);
            matrices.translate(0.015F, 0.01F, 0.01F);
            renderer.renderItem(entity, stack, mode, left, matrices, glow, world, light,
                    OverlayTexture.DEFAULT_UV, seed);
            matrices.translate(-0.03F, -0.02F, -0.02F);
            renderer.renderItem(entity, stack, mode, left, matrices, glow, world, light,
                    OverlayTexture.DEFAULT_UV, seed);
        } finally {
            matrices.pop();
        }
    }

    private static boolean isLegacyStyle() {
        return "legacy".equals(Config.getInstance().magicGlowStyle);
    }

    private static void renderCaptured(ItemRenderer renderer, @Nullable LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, boolean left, MatrixStack matrices, VertexConsumerProvider buffers,
            @Nullable World world, int light, int seed, int glowColor, boolean firstPerson, LevitationTrail trail) {
        Set<RenderLayer> itemLayers = new LinkedHashSet<>();
        ItemAuraGeometry.Capture capture = new ItemAuraGeometry.Capture(matrices.peek());
        VertexConsumerProvider recorder = layer -> {
            itemLayers.add(layer);
            VertexConsumer original = buffers.getBuffer(layer);
            if (layer.getVertexFormat() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                    || layer.getDrawMode() != VertexFormat.DrawMode.QUADS) return original;
            return RenderLayerHelper.getTexture(layer).map(texture -> capture.wrap(original, texture)).orElse(original);
        };
        // 原物品只渲染一次；附魔、模型覆写与特殊物品 renderer 保持原样。
        renderer.renderItem(entity, stack, mode, left, matrices, recorder, world, light, OverlayTexture.DEFAULT_UV, seed);
        ItemAuraGeometry.Mesh mesh = capture.finish();
        if (mesh.isEmpty()) return;
        LevitationTrail centeredTrail = trail.atCenter(mesh.centerInRender());
        if (!HornAuraPass.isWorld() && !firstPerson && buffers instanceof VertexConsumerProvider.Immediate immediate)
            itemLayers.forEach(immediate::draw);
        float delta = MinecraftClient.getInstance().getTickDelta();
        double ticks = entity != null ? (double) entity.age + delta
                : world != null ? world.getTime() + (double) delta : Util.getMeasuringTimeMs() / 50.0;
        int clock = (int) Math.round((ticks % 240 + 240) % 240 * 50);
        int sparkleSeed = seed ^ (entity == null ? 0 : entity.getUuid().hashCode())
                ^ stack.getItem().hashCode() ^ (left ? 0x6E624EB7 : 0x38D12A45);
        for (int index = 0; index < mesh.batches.size(); index++) {
            ItemAuraGeometry.Batch batch = mesh.batches.get(index);
            boolean last = index == mesh.batches.size() - 1;
            HornAuraPass.submit(batch.texture(), glow -> {
                mesh.render(batch, glow, glowColor, clock);
                if (last) mesh.stars(glow, glowColor, ticks, sparkleSeed, clock);
            });
        }
        if (!centeredTrail.isEmpty()) HornAuraPass.submit(glow -> centeredTrail.render(glow, glowColor, clock));
    }
}
