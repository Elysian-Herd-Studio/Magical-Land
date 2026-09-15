package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class SectionLabel extends ClickableWidget {
    public SectionLabel(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        this.active = false;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.alpha < 0.05f) return;
        int combinedAlpha = (int) (136 * this.alpha);
        if (combinedAlpha < 5) return;
        int textColor = (combinedAlpha << 24) | 0xFFFFFF;
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(),
                this.getX() + 6, this.getY() + (this.height - 8) / 2, textColor);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
