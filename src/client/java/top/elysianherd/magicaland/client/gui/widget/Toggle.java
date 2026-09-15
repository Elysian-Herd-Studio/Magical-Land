package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class Toggle extends PressableWidget {
    private final Consumer<Toggle> onPress;
    private float currentAlpha;
    private boolean state;
    private float togglePosition;

    public Toggle(int x, int y, int width, int height, Text message, boolean initialState,
            Consumer<Toggle> onPress) {
        super(x, y, width, height, message);
        this.state = initialState;
        this.onPress = onPress;
        this.currentAlpha = 0.15f;
        this.togglePosition = initialState ? 1.0f : 0.0f;
    }

    public boolean getState() {
        return this.state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    @Override
    public void onPress() {
        this.state = !this.state;
        if (this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
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

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.alpha < 0.05f) return;
        boolean hovered = this.isHovered();
        float targetAlpha = 0.15f;

        if (!this.active || hovered) {
            targetAlpha = 0.35f;
        }

        if (currentAlpha < targetAlpha) {
            currentAlpha = Math.min(targetAlpha, currentAlpha + 0.05f);
        } else if (currentAlpha > targetAlpha) {
            currentAlpha = Math.max(targetAlpha, currentAlpha - 0.05f);
        }

        int alpha = (int) (currentAlpha * this.alpha * 255);
        int textAlpha = (int) (this.alpha * 255);

        if (alpha > 0) {
            fillRoundedRect(context, this.getX(), this.getY(), this.width, this.height, (alpha << 24) | 0xFFFFFF);
        }

        if (textAlpha > 0) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(),
                    this.getX() + 6, this.getY() + (this.height - 8) / 2, (textAlpha << 24) | 0xFFFFFF);
        }

        float targetPosition = this.state ? 1.0f : 0.0f;
        float diff = targetPosition - this.togglePosition;
        if (Math.abs(diff) > 0.01f) {
            this.togglePosition += diff * 0.3f;
        } else {
            this.togglePosition = targetPosition;
        }

        int toggleWidth = 30;
        int toggleHeight = 14;
        int toggleX = this.getX() + this.width - toggleWidth - 3;
        int toggleY = this.getY() + (this.height - toggleHeight) / 2;

        int r = Math.round(117 + (76 - 117) * this.togglePosition);
        int g = Math.round(117 + (175 - 117) * this.togglePosition);
        int b = Math.round(117 + (80 - 117) * this.togglePosition);
        int finalBgColor = (textAlpha << 24) | (r << 16) | (g << 8) | b;

        if (textAlpha > 0) {
            fillRoundedRect(context, toggleX, toggleY, toggleWidth, toggleHeight, finalBgColor);

            int knobWidth = 10;
            int knobHeight = 10;
            int knobMinX = toggleX + 2;
            int knobMaxX = toggleX + toggleWidth - knobWidth - 2;
            int knobX = Math.round(knobMinX + (knobMaxX - knobMinX) * this.togglePosition);
            int knobY = toggleY + (toggleHeight - knobHeight) / 2;

            fillRoundedRect(context, knobX, knobY, knobWidth, knobHeight, (textAlpha << 24) | 0xFFFFFF);
        }
    }
}
