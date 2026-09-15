package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

final class HornAuraPass {
    private static final List<Draw> PENDING = new ArrayList<>();
    private static final List<Draw> HANDS = new ArrayList<>();
    private static final BufferBuilder BUFFER = new BufferBuilder(32768);
    private static VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(BUFFER);
    private static boolean world, accepting, hands;

    private HornAuraPass() {}

    static void init() {
        WorldRenderEvents.START.register(context -> {
            clear();
            world = accepting = true;
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            accepting = false;
            if (context.advancedTranslucency()) drawWorld(true);
        });
        WorldRenderEvents.LAST.register(context -> {
            accepting = false;
            // 普通画质先画云再加光；Fabulous 已交给透明层按深度合成。
            if (!context.advancedTranslucency()) drawWorld(false);
        });
        WorldRenderEvents.END.register(context -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    static boolean isWorld() { return world; }

    private static void drawWorld(boolean fabulous) {
        try {
            draw(PENDING, fabulous);
        } finally {
            PENDING.clear();
        }
    }

    static void beginHands() {
        HANDS.clear();
        hands = true;
    }

    static void endHands() {
        hands = false;
        try {
            draw(HANDS, false);
        } finally {
            HANDS.clear();
        }
    }

    static void discardHands() {
        HANDS.clear();
        hands = false;
    }

    static void submit(Consumer<VertexConsumer> geometry) {
        submit(null, geometry);
    }

    static void submit(Identifier texture, Consumer<VertexConsumer> geometry) {
        Draw draw = new Draw(new Matrix4f(RenderSystem.getModelViewMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting(), texture, geometry);
        if (world) {
            if (accepting && PENDING.size() < 256) PENDING.add(draw);
        } else if (hands) {
            if (HANDS.size() < 64) HANDS.add(draw);
        } else {
            draw(List.of(draw), false);
        }
    }

    private static void draw(List<Draw> draws, boolean fabulous) {
        if (draws.isEmpty()) return;
        var viewStack = RenderSystem.getModelViewStack();
        var previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var previousSorting = RenderSystem.getVertexSorting();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        RenderLayer layer = null;
        viewStack.push();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            Draw previous = null;
            for (Draw draw : draws) {
                RenderLayer nextLayer = MagicGlow.aura(fabulous, draw.texture);
                if (previous == null || !draw.view.equals(previous.view) || !draw.projection.equals(previous.projection)
                        || draw.sorting != previous.sorting || layer != nextLayer) {
                    if (previous != null) flush(layer);
                    viewStack.peek().getPositionMatrix().set(draw.view);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(draw.projection, draw.sorting);
                }
                layer = nextLayer;
                draw.geometry.accept(consumers.getBuffer(layer));
                previous = draw;
            }
            flush(layer);
        } finally {
            try {
                if (BUFFER.isBuilding()) discardBuffer();
            } finally {
                viewStack.pop();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
                RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            }
        }
    }

    private static void flush(RenderLayer layer) {
        try {
            consumers.draw(layer);
        } catch (RuntimeException | Error failure) {
            // 原版 RenderLayer.draw 异常时不会自动恢复 target / depth / blend。
            try { layer.endDrawing(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            try { discardBuffer(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void discardBuffer() {
        try {
            if (BUFFER.isBuilding()) {
                var unfinished = BUFFER.endNullable();
                if (unfinished != null) unfinished.release();
            }
        } finally {
            BUFFER.clear();
            consumers = VertexConsumerProvider.immediate(BUFFER);
        }
    }

    private static void clear() {
        PENDING.clear();
        HANDS.clear();
        world = accepting = hands = false;
    }

    private record Draw(Matrix4f view, Matrix4f projection, VertexSorter sorting, Identifier texture,
                        Consumer<VertexConsumer> geometry) {}
}
