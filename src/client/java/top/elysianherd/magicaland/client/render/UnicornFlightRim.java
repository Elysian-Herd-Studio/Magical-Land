package top.elysianherd.magicaland.client.render;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.api.FirstPersonItemView;
import top.elysianherd.magicaland.client.config.Config;

/** 标准 GUI 彩色软边，仅显示本体的魔法飞行状态。 */
public final class UnicornFlightRim {
    private static final UnicornFlightRimMath.Entrance ENTRANCE = new UnicornFlightRimMath.Entrance();
    private static Object world, player;
    private static boolean initialized;
    private UnicornFlightRim() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        HudRenderCallback.EVENT.register(UnicornFlightRim::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() { ENTRANCE.reset(); world = player = null; }

    private static void render(DrawContext context, float delta) {
        var client = MinecraftClient.getInstance();
        var self = client.player;
        if (self == null || client.world == null || self.getWorld() != client.world
                || client.currentScreen != null || client.getOverlay() != null || client.options.hudHidden
                || !client.options.getPerspective().isFirstPerson()
                || client.getCameraEntity() != self || client.gameRenderer.getCamera().getFocusedEntity() != self
                || FirstPersonItemView.forOwner(self) != null || !Config.getInstance().replacePlayerModel
                || !PonyFlightVisuals.eligible(self)) { reset(); return; }
        var model = PonyFlightVisuals.config(self);
        if (model == null || !model.showHorn || model.showWings) { reset(); return; }
        if (world != client.world || player != self) { reset(); world = client.world; player = self; }
        float partial = UnicornFlightRimMath.clamp(delta);
        double ticks = self.age + partial;
        float magic = ENTRANCE.sample(ticks, PonyFlightVisuals.sample(self, model, partial).magic())
                * PonyFlightVisuals.auraBrightness(self, partial);
        if (magic <= .001f) return;
        var vertices = context.getVertexConsumers().getBuffer(RenderLayer.getGuiOverlay());
        var matrix = context.getMatrices().peek().getPositionMatrix();
        UnicornFlightRimMath.emit(context.getScaledWindowWidth(), context.getScaledWindowHeight(), magic,
                GlowingItem.getGlowColor(model), ticks, (x, y, red, green, blue, alpha) ->
                        vertices.vertex(matrix, x, y, 0).color(red, green, blue, alpha).next());
    }
}
