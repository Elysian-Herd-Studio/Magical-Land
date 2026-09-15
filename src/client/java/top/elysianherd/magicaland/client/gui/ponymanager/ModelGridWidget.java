package top.elysianherd.magicaland.client.gui.ponymanager;

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
import top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.elysianherd.magicaland.client.gui.ponycustom.PonyStyleThumbnails;

public final class ModelGridWidget extends ClickableWidget {
    public record ModelEntry(String key, String name, ModelConfig preview, Text badge) {
        public ModelEntry(String name, ModelConfig preview) { this(name, name, preview, null); }
    }

    private final List<ModelEntry> models;
    private final ModelGridLayout layout;
    private final Supplier<String> selectedName;
    private final Consumer<String> onSelect;
    private int cursor;
    private double scroll;
    private boolean draggingScrollbar;
    private double scrollbarOffset;

    public ModelGridWidget(Rect bounds, List<ModelEntry> models, Supplier<String> selectedName,
            Consumer<String> onSelect) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), text("models"));
        this.models = List.copyOf(models);
        this.layout = ModelGridLayout.of(bounds.width() - 12, models.size());
        this.selectedName = selectedName;
        this.onSelect = onSelect;
        cursor = Math.max(0, selectedIndex());
        revealCursor();
    }

    public double getScrollAmount() { return scroll; }
    public void restoreScrollAmount(double amount) { scroll = Math.max(0, Math.min(maxScroll(), amount)); }
    private double maxScroll() { return Math.max(0, layout.height() - (getHeight() - 6)); }

    private int selectedIndex() {
        String selected = selectedName.get();
        for (int i = 0; i < models.size(); i++) if (models.get(i).key().equals(selected)) return i;
        return -1;
    }

    private int indexAt(double x, double y) {
        if (x < getX() + 3 || x >= getX() + getWidth() - 9
                || y < getY() + 3 || y >= getY() + getHeight() - 3) return -1;
        return layout.indexAt(x - getX() - 3, y - getY() - 3 + (int) scroll);
    }

    private void fillRoundedRect(DrawContext context, int x, int y, int width, int height, int color) {
        int right = x + width;
        int bottom = y + height;
        context.fill(x + 2, y, right - 2, y + 1, color);
        context.fill(x + 1, y + 1, right - 1, y + 2, color);
        context.fill(x, y + 2, right, bottom - 2, color);
        context.fill(x + 1, bottom - 2, right - 1, bottom - 1, color);
        context.fill(x + 2, bottom - 1, right - 2, bottom, color);
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        var font = MinecraftClient.getInstance().textRenderer;
        int hovered = indexAt(mouseX, mouseY);
        int selected = selectedIndex();
        context.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            for (int i = 0; i < models.size(); i++) {
                int x = getX() + 3 + layout.x(i);
                int y = getY() + 3 + layout.y(i) - (int) scroll;
                if (y >= getY() + getHeight() || y + ModelGridLayout.CARD_HEIGHT <= getY()) continue;
                int width = layout.cardWidth();
                boolean highlighted = i == selected || i == hovered || isFocused() && i == cursor;
                int bgAlpha = (int) ((highlighted ? 0.35f : 0.15f) * alpha * 255);
                int textAlpha = (int) (alpha * 255);
                if (bgAlpha > 0)
                    fillRoundedRect(context, x, y, width, ModelGridLayout.CARD_HEIGHT, (bgAlpha << 24) | 0xFFFFFF);
                ModelEntry model = models.get(i);
                if (model.preview() != null) {
                    PonyStyleThumbnails.renderModel(context, model.preview(), x + 3, y + 3,
                            width - 6, ModelGridLayout.CARD_HEIGHT - 19);
                } else {
                    context.drawCenteredTextWithShadow(font, "!", x + width / 2, y + 30, (textAlpha << 24) | 0xFF9999);
                }
                if (i == selected) context.drawTextWithShadow(font, "✓", x + 4, y + 4, (textAlpha << 24) | 0xFFFFFF);
                if (model.badge() != null) {
                    String badge = font.trimToWidth(model.badge().getString(), Math.max(1, width - 22));
                    int badgeX = x + width - font.getWidth(badge) - 4;
                    context.fill(badgeX - 2, y + 2, x + width - 2, y + 13, (bgAlpha << 24));
                    context.drawTextWithShadow(font, badge, badgeX, y + 4, (textAlpha << 24) | 0xDDDDDD);
                }
                context.drawCenteredTextWithShadow(font, font.trimToWidth(model.name(), width - 8),
                        x + width / 2, y + ModelGridLayout.CARD_HEIGHT - 12,
                        (textAlpha << 24) | (model.preview() == null ? 0xFF9999 : 0xFFFFFF));
            }
        } finally {
            context.disableScissor();
        }
        if (models.isEmpty()) context.drawCenteredTextWithShadow(font, text("empty"),
                getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, 0xFFAAAAAA);
        if (maxScroll() > 0) {
            int left = getX() + getWidth() - 6;
            context.fill(left, getY() + 3, left + 3, getY() + getHeight() - 3, 0x22FFFFFF);
            int top = scrollbarTop();
            context.fill(left, top, left + 3, top + scrollbarHeight(), 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(x, y)) return false;
        draggingScrollbar = false;
        if (maxScroll() > 0 && x >= getX() + getWidth() - 9) {
            draggingScrollbar = true;
            int top = scrollbarTop();
            scrollbarOffset = y >= top && y < top + scrollbarHeight() ? y - top : scrollbarHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        int index = indexAt(x, y);
        if (index < 0) return false;
        cursor = index;
        choose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!active || !visible || !isMouseOver(x, y)) return false;
        restoreScrollAmount(scroll - amount * 28);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !draggingScrollbar) return false;
        dragScrollbar(y);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }

    private int scrollbarHeight() {
        int track = getHeight() - 6;
        return Math.min(track, Math.max(12, track * track / Math.max(1, layout.height())));
    }

    private int scrollbarTop() {
        return getY() + 3 + (int) ((getHeight() - 6 - scrollbarHeight()) * scroll / Math.max(1, maxScroll()));
    }

    private void dragScrollbar(double y) {
        int travel = getHeight() - 6 - scrollbarHeight();
        restoreScrollAmount((y - getY() - 3 - scrollbarOffset) / Math.max(1, travel) * maxScroll());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused() || models.isEmpty()) return false;
        if (key == 257 || key == 335 || key == 32) { choose(); return true; }
        if (key != 262 && key != 263 && key != 264 && key != 265 && key != 268 && key != 269) return false;
        cursor = layout.move(cursor, key);
        revealCursor();
        return true;
    }

    private void choose() {
        if (cursor < 0 || cursor >= models.size()) return;
        onSelect.accept(models.get(cursor).key());
        playDownSound(MinecraftClient.getInstance().getSoundManager());
    }

    private void revealCursor() {
        if (models.isEmpty()) return;
        int top = layout.y(cursor);
        if (top < scroll) restoreScrollAmount(top);
        else if (top + ModelGridLayout.CARD_HEIGHT > scroll + getHeight() - 6)
            restoreScrollAmount(top + ModelGridLayout.CARD_HEIGHT - getHeight() + 6);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused && !draggingScrollbar) revealCursor();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        if (!models.isEmpty()) {
            ModelEntry model = models.get(cursor);
            Text label = Text.literal(model.name());
            if (model.badge() != null) label = label.copy().append(" · ").append(model.badge());
            builder.put(NarrationPart.TITLE, label);
        }
        builder.put(NarrationPart.USAGE, text("select_hint"));
    }

    private static Text text(String key) { return Text.translatable("text.magicaland.pony_manager." + key); }
}
