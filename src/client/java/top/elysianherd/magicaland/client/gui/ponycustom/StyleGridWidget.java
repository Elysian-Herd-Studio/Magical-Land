package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStyleDefinition;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

public final class StyleGridWidget extends ClickableWidget {
    private final ModelConfig config;
    private final PonyStylePart part;
    private final List<PonyStyleDefinition> styles;
    private final StyleGridLayout layout;
    private final Supplier<String> selectedId;
    private final Consumer<String> onSelect;
    private final SettingsList list;
    private int cursor;
    private int viewportTop;
    private int viewportBottom = Integer.MAX_VALUE;

    public StyleGridWidget(int x, int width, ModelConfig config, PonyStylePart part, Supplier<String> selectedId,
            Consumer<String> onSelect, SettingsList list) {
        super(x, 0, width, StyleGridLayout.of(width, PonyStyleRegistry.stylesFor(part).size()).height(),
                Text.translatable("text.magicaland.customize.styles.pool"));
        this.config = config;
        this.part = part;
        this.styles = PonyStyleRegistry.stylesFor(part);
        this.layout = StyleGridLayout.of(width, styles.size());
        this.selectedId = selectedId;
        this.onSelect = onSelect;
        this.list = list;
        this.cursor = Math.max(0, indexOf(selectedId.get()));
    }

    public void setViewport(int top, int bottom) {
        viewportTop = top;
        viewportBottom = bottom;
    }

    private int indexOf(String id) {
        for (int i = 0; i < styles.size(); i++) if (styles.get(i).id.equals(id)) return i;
        return -1;
    }

    private Text styleText(int index) {
        String key = switch (part) {
            case FRONT_MANE -> "front_mane_style";
            case BACK_MANE -> "back_mane_style";
            case TAIL -> "tail_style";
            case EYE -> "eye_style";
        };
        return Text.translatable("text.magicaland.config." + key + ".name").copy()
                .append(" " + styles.get(index).id);
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
    public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
        int hovered = mouseY >= viewportTop && mouseY < viewportBottom
                ? layout.indexAt(mouseX - getX(), mouseY - getY()) : -1;
        int selected = indexOf(selectedId.get());
        var font = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < styles.size(); i++) {
            if (!layout.visible(i, getY(), viewportTop, viewportBottom)) continue;
            int x = getX() + layout.x(i);
            int y = getY() + layout.y(i);
            int cardWidth = layout.cardWidth();
            int bgAlpha = (int) ((i == selected || i == hovered ? 0.35f : 0.15f) * alpha * 255);
            int textAlpha = (int) (alpha * 255);
            if (bgAlpha > 0)
                fillRoundedRect(draw, x, y, cardWidth, StyleGridLayout.CARD_HEIGHT, (bgAlpha << 24) | 0xFFFFFF);
            PonyStyleThumbnails.render(draw, config, part, styles.get(i).id, x + 3, y + 3,
                    cardWidth - 6, StyleGridLayout.CARD_HEIGHT - 19);
            if (i == selected) draw.drawTextWithShadow(font, "✓", x + 4, y + 4, (textAlpha << 24) | 0xFFFFFF);
            draw.drawCenteredTextWithShadow(font, styles.get(i).id, x + cardWidth / 2,
                    y + StyleGridLayout.CARD_HEIGHT - 12, (textAlpha << 24) | 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible || button != 0 || y < viewportTop || y >= viewportBottom) return false;
        int index = layout.indexAt(x - getX(), y - getY());
        if (index < 0) return false;
        cursor = index;
        setFocused(true);
        choose();
        playDownSound(MinecraftClient.getInstance().getSoundManager());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused() || styles.isEmpty()) return false;
        if (key == 257 || key == 335 || key == 32) {
            choose();
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }
        if (key != 262 && key != 263 && key != 264 && key != 265 && key != 268 && key != 269) return false;
        cursor = layout.move(cursor, key);
        revealCursor();
        setMessage(styleText(cursor));
        return true;
    }

    private void choose() {
        if (cursor >= 0 && cursor < styles.size()) onSelect.accept(styles.get(cursor).id);
    }

    private void revealCursor() {
        list.ensureWidgetAreaVisible(this, layout.y(cursor), layout.y(cursor) + StyleGridLayout.CARD_HEIGHT);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused && !styles.isEmpty()) revealCursor();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        if (!styles.isEmpty()) builder.put(NarrationPart.TITLE, styleText(cursor));
        builder.put(NarrationPart.USAGE, Text.translatable("text.magicaland.customize.styles.choose_hint"));
    }
}
