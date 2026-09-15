package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;

/** 一行内左侧放镜像按钮、右侧放颜色选择器；事件转发给内嵌的颜色选择器。 */
final class MirrorColorRow extends ClickableWidget {
    private final ColorPicker picker;
    private final Runnable onMirror;
    private final int mirrorWidth;
    private float currentAlpha = 0.15f;

    MirrorColorRow(int x, int width, int mirrorWidth, ColorPicker picker, Runnable onMirror) {
        super(x, 0, width, 20, Text.translatable("text.magicaland.customize.mark.mirror"));
        this.picker = picker;
        this.onMirror = onMirror;
        this.mirrorWidth = mirrorWidth;
    }

    private void layoutPicker() {
        picker.setX(getX() + mirrorWidth + 4);
        picker.setY(getY());
    }

    @Override public void setX(int x) { super.setX(x); if (picker != null) layoutPicker(); }
    @Override public void setY(int y) { super.setY(y); if (picker != null) layoutPicker(); }
    @Override public void setAlpha(float alpha) { super.setAlpha(alpha); if (picker != null) picker.setAlpha(alpha); }

    private boolean overMirror(double x, double y) {
        return x >= getX() && x < getX() + mirrorWidth && y >= getY() && y < getY() + height;
    }

    private void fillRoundedRect(DrawContext context, int x, int y, int width, int height, int color) {
        int x1 = x;
        int y1 = y;
        int x2 = x + width;
        int y2 = y + height;
        context.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        context.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        context.fill(x1, y1 + 2, x2, y2 - 2, color);
        context.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        context.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered() && overMirror(mouseX, mouseY);
        float targetAlpha = hovered ? 0.35f : 0.15f;
        if (currentAlpha < targetAlpha) currentAlpha = Math.min(targetAlpha, currentAlpha + 0.05f);
        else if (currentAlpha > targetAlpha) currentAlpha = Math.max(targetAlpha, currentAlpha - 0.05f);
        int bgAlpha = (int) (currentAlpha * alpha * 255);
        int textAlpha = (int) (alpha * 255);
        if (bgAlpha > 0) fillRoundedRect(draw, getX(), getY(), mirrorWidth, height, (bgAlpha << 24) | 0xFFFFFF);
        var font = MinecraftClient.getInstance().textRenderer;
        draw.drawCenteredTextWithShadow(font, font.trimToWidth(getMessage().getString(), Math.max(0, mirrorWidth - 8)),
                getX() + mirrorWidth / 2, getY() + (height - 8) / 2, (textAlpha << 24) | 0xFFFFFF);
        picker.render(draw, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible) return false;
        if (overMirror(x, y)) {
            if (button == 0) {
                onMirror.run();
                playDownSound(MinecraftClient.getInstance().getSoundManager());
            }
            return true;
        }
        return picker.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) { return picker.mouseReleased(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return picker.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) { return picker.keyPressed(key, scanCode, modifiers); }
    @Override public boolean charTyped(char chr, int modifiers) { return picker.charTyped(chr, modifiers); }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
