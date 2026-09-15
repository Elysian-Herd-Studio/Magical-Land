package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class SettingsTextField extends TextFieldWidget {
    private boolean expanded;
    private float currentAlpha = 0.15f;

    public SettingsTextField(int x, int y, int width, int height, Text message) {
        super(MinecraftClient.getInstance().textRenderer, x, y, width, height, message);
        setDrawsBackground(false);
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!expanded && active && visible && button == 0 && isMouseOver(mouseX, mouseY)) {
            expanded = true;
            setDrawsBackground(true);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expanded && keyCode == 256) {
            expanded = false;
            setDrawsBackground(false);
            setFocused(false);
            return true;
        }
        if (expanded && keyCode == 257) {
            expanded = false;
            setDrawsBackground(false);
            setFocused(false);
            return true;
        }
        return expanded && super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (expanded) {
            super.renderButton(context, mouseX, mouseY, delta);
            return;
        }

        float targetAlpha = !active || isHovered() ? 0.35f : 0.15f;
        if (currentAlpha < targetAlpha) currentAlpha = Math.min(targetAlpha, currentAlpha + 0.05f);
        else if (currentAlpha > targetAlpha) currentAlpha = Math.max(targetAlpha, currentAlpha - 0.05f);
        float buttonAlpha = this.alpha;
        if (!active) buttonAlpha *= 0.4f;
        int backgroundAlpha = (int) (currentAlpha * buttonAlpha * 255);
        int textAlpha = (int) (buttonAlpha * 255);
        if (backgroundAlpha <= 0 || textAlpha <= 0) return;
        int backgroundColor = (backgroundAlpha << 24) | 0xFFFFFF;
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        fillRoundedRect(context, getX(), getY(), width, height, backgroundColor);

        var renderer = MinecraftClient.getInstance().textRenderer;
        int textY = getY() + (height - 8) / 2;
        context.drawTextWithShadow(renderer, getMessage(), getX() + 6, textY, textColor);
        String value = getText().isBlank() ? "-" : getText();
        String clipped = renderer.trimToWidth(value, Math.max(16, width / 2));
        int valueWidth = renderer.getWidth(clipped);
        context.drawTextWithShadow(renderer, clipped, getX() + width - valueWidth - 6, textY, textColor);
    }

    private static void fillRoundedRect(DrawContext context, int x, int y, int width, int height, int color) {
        int right = x + width;
        int bottom = y + height;
        context.fill(x + 2, y, right - 2, y + 1, color);
        context.fill(x + 1, y + 1, right - 1, y + 2, color);
        context.fill(x, y + 2, right, bottom - 2, color);
        context.fill(x + 1, bottom - 2, right - 1, bottom - 1, color);
        context.fill(x + 2, bottom - 1, right - 2, bottom, color);
    }
}
