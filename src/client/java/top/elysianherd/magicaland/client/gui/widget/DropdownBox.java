package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.Consumer;

public class DropdownBox extends ClickableWidget {
    private final List<String> options;
    private int selectedIndex;
    private boolean open;
    private final Consumer<Integer> onSelect;
    private float animationProgress = 0f;

    public DropdownBox(int x, int y, int width, int height, List<String> options, int initialIndex, Consumer<Integer> onSelect) {
        super(x, y, width, height, Text.empty());
        this.options = options;
        this.selectedIndex = (initialIndex >= 0 && initialIndex < options.size()) ? initialIndex : -1;
        this.onSelect = onSelect;
        this.open = false;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < options.size()) {
            this.selectedIndex = index;
        } else {
            this.selectedIndex = -1;
        }
    }

    public int getSelectedIndex() {
        return this.selectedIndex;
    }

    public String getSelectedOption() {
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            return options.get(selectedIndex);
        }
        return null;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;

        if (this.open) {
            int optionHeight = this.height;
            for (int i = 0; i < options.size(); i++) {
                int optY = this.getY() + this.height + i * optionHeight;
                if (mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= optY && mouseY <= optY + optionHeight) {
                    this.selectedIndex = i;
                    this.open = false;
                    if (this.onSelect != null) {
                        this.onSelect.accept(i);
                    }
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    return true;
                }
            }
            if (!(mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height)) {
                this.open = false;
                return false;
            }
        }

        if (this.clicked(mouseX, mouseY)) {
            this.open = !this.open;
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }

        return false;
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
        if (!this.visible) return;

        float targetAlpha = this.isHovered() ? 0.35f : 0.15f;
        if (animationProgress < targetAlpha) {
            animationProgress = Math.min(targetAlpha, animationProgress + 0.05f);
        } else if (animationProgress > targetAlpha) {
            animationProgress = Math.max(targetAlpha, animationProgress - 0.05f);
        }

        int alpha = (int) (animationProgress * 255);
        int bgColor = (alpha << 24) | 0xFFFFFF;

        fillRoundedRect(context, this.getX(), this.getY(), this.width, this.height, bgColor);

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        String text = selectedIndex >= 0 && selectedIndex < options.size() ? options.get(selectedIndex) : Text.translatable("text.magicaland.config.dropdown.select").getString();
        int textY = this.getY() + (this.height - 8) / 2;
        
        context.drawTextWithShadow(textRenderer, Text.literal(text), this.getX() + 6, textY, 0xFFFFFF);

        String arrow = open ? "▲" : "▼";
        int arrowWidth = textRenderer.getWidth(arrow);
        context.drawTextWithShadow(textRenderer, Text.literal(arrow), this.getX() + this.width - arrowWidth - 8, textY, 0xCCCCCC);

        if (open) {
            int optionHeight = this.height;
            int totalListHeight = options.size() * optionHeight;
            
            fillRoundedRect(context, this.getX(), this.getY() + this.height + 2, this.width, totalListHeight, 0x22FFFFFF);

            for (int i = 0; i < options.size(); i++) {
                int optY = this.getY() + this.height + 2 + i * optionHeight;
                boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= optY && mouseY <= optY + optionHeight;
                
                if (hovered) {
                    fillRoundedRect(context, this.getX() + 2, optY + 1, this.width - 4, optionHeight - 2, 0x44FFFFFF);
                }
                
                int itemColor = (i == selectedIndex) ? 0xFF55FF55 : 0xFFFFFFFF;
                context.drawTextWithShadow(textRenderer, Text.literal(options.get(i)), this.getX() + 8, optY + (optionHeight - 8) / 2, itemColor);
            }
        }
    }
}
