package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class TabButton extends PressableWidget {
    private final Consumer<TabButton> onPress;

    public TabButton(int x, int y, int width, int height, Text message, boolean isSelected,
            Consumer<TabButton> onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.active = !isSelected;
    }

    @Override
    public void onPress() {
        if (this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();
        boolean isSelected = !this.active;

        int textColor = isSelected ? 0xFFFFFFFF : (hovered ? 0xFFDDDDDD : 0xFFAAAAAA);

        int textX = this.getX() + 8;

        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(),
                textX, this.getY() + (this.height - 8) / 2, textColor);
    }
}
