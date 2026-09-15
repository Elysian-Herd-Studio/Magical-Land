package top.elysianherd.magicaland.client.emote;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public final class EmoteWheelScreen extends Screen {
    private static final String[] ENTRIES = { "wave", "ballet" };
    private static final double SECTOR = EmoteWheelLayout.SECTOR;
    private static KeyBinding wheelKey;
    private EmoteWheelPixels pixels;
    private int selected = -1;
    private boolean keyboardSelection;
    private boolean releaseHandled;
    private boolean unavailable;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public EmoteWheelScreen() {
        super(Text.translatable("text.magicaland.emote.wheel"));
    }

    @Override
    protected void init() {
        pixels = EmoteWheelPixels.create(width, height);
    }

    public static void register() {
        wheelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magicaland.emote",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.magicaland.keys"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (wheelKey.wasPressed()) {
                if (client.currentScreen == null && client.isWindowFocused() && EmoteClient.canOpen()) {
                    client.setScreen(new EmoteWheelScreen());
                }
            }
        });
    }

    private static boolean keyHeld() {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(wheelKey);
        if (key.getCode() < 0) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        }
        // Scan-code bindings are released by keyReleased instead of key-symbol polling.
        return key.getCategory() != InputUtil.Type.KEYSYM || InputUtil.isKeyPressed(window, key.getCode());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (client == null || !client.isWindowFocused() || !EmoteClient.canOpen()) {
            close();
        } else if (!releaseHandled && !keyHeld()) {
            releaseHandled = true;
            choose();
        }
    }

    private double radius() {
        return EmoteWheelLayout.radius(width, height);
    }

    private int entryAt(double mouseX, double mouseY) {
        return EmoteWheelLayout.entryAt(width, height, mouseX, mouseY);
    }

    private void trackMouse(double mouseX, double mouseY) {
        if (Double.isNaN(lastMouseX)) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return;
        }
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            keyboardSelection = false;
            unavailable = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            selected = entryAt(mouseX, mouseY);
        } else if (!keyboardSelection) {
            selected = entryAt(mouseX, mouseY);
        }
    }

    private void choose() {
        if (client == null || !client.isWindowFocused() || !EmoteClient.canOpen() || selected < 0) {
            close();
        } else if (EmoteClient.select(selected == EmoteWheelLayout.STOP ? "stop" : ENTRIES[selected])) {
            close();
        } else {
            unavailable = true;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        trackMouse(mouseX, mouseY);
        context.fill(0, 0, width, height, 0x50000000);
        double radius = radius();
        String active = EmoteClient.selectedAction();
        VertexConsumer vertices = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        for (var span : pixels.spans()) rectangle(vertices, matrix, span, pixels.step(), 0x70000000);
        for (var span : pixels.spans()) {
            boolean playing = span.slot() < ENTRIES.length && ENTRIES[span.slot()].equals(active);
            rectangle(vertices, matrix, span, 0, EmoteWheelPixels.color(span.shade(), selected == span.slot(), playing));
        }
        context.draw();
        int textWidth = Math.max(24, (int) (radius * .60));
        for (int entry = 0; entry < ENTRIES.length; entry++) {
            double angle = EmoteWheelLayout.FIRST_ANGLE + entry * SECTOR;
            int x = width / 2 + (int) Math.round(Math.cos(angle) * radius * .71);
            int y = height / 2 + (int) Math.round(Math.sin(angle) * radius * .71) - 4;
            context.drawCenteredTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(label(entry).getString(), textWidth), x, y,
                    entry == selected ? 0xFFFFFF : 0xDFDFDF);
            if (ENTRIES[entry].equals(active)) {
                context.fill(x + 12, y - 9, x + 18, y - 3, 0xFF191919);
                context.fill(x + 13, y - 8, x + 17, y - 4, 0xFF79BF65);
                context.fill(x + 13, y - 8, x + 16, y - 7, 0xFFC1E9AB);
            }
        }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2,
                Math.max(4, height / 2 - (int) radius - 16), 0xFFFFFF);
        Text center = label(EmoteWheelLayout.STOP);
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(center.getString(), (int) (radius * .62)),
                width / 2, height / 2 - 4, 0xFFFFFF);
        Text hint = Text.translatable(unavailable ? "text.magicaland.emote.unavailable" : "text.magicaland.emote.hint");
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(hint.getString(), Math.max(0, width - 16)), width / 2,
                Math.min(height - 12, height / 2 + (int) radius + 8), unavailable ? 0xFFD0B0 : 0xD5DCE5);
        super.render(context, mouseX, mouseY, delta);
    }

    private static void rectangle(VertexConsumer vertices, Matrix4f matrix, EmoteWheelPixels.Span span, int offset, int color) {
        int x = span.x() + offset, y = span.y() + offset;
        vertices.vertex(matrix, x, y + span.height(), 0).color(color).next();
        vertices.vertex(matrix, x + span.width(), y + span.height(), 0).color(color).next();
        vertices.vertex(matrix, x + span.width(), y, 0).color(color).next();
        vertices.vertex(matrix, x, y, 0).color(color).next();
    }

    private static Text label(int entry) {
        return Text.translatable("text.magicaland.emote." + (entry == EmoteWheelLayout.STOP ? "stop" : ENTRIES[entry]));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        trackMouse(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selected = entryAt(mouseX, mouseY);
            choose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!releaseHandled && wheelKey.matchesMouse(button)) {
            selected = entryAt(mouseX, mouseY);
            releaseHandled = true;
            choose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
            int direction = keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP
                    || keyCode == GLFW.GLFW_KEY_TAB && hasShiftDown() ? -1 : 1;
            selected = selected < 0 ? 0 : Math.floorMod(selected + direction, ENTRIES.length + 1);
            keyboardSelection = true;
            unavailable = false;
            if (client != null) client.getNarratorManager().narrate(label(selected));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            choose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!releaseHandled && wheelKey.matchesKey(keyCode, scanCode)) {
            releaseHandled = true;
            choose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}
