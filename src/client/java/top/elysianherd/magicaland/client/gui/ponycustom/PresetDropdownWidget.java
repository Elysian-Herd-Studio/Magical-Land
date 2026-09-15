package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;

public final class PresetDropdownWidget extends ClickableWidget {
    private final Consumer<String> select;
    private final Runnable manage, beforeOpen;
    private boolean open;
    private List<String> names = List.of();
    private int first, focusedRow;
    private long lastDirtyCheck;
    private boolean dirty;
    private float currentAlpha = 0.15f;
    private final int popupWidth;

    public PresetDropdownWidget(int x, int y, int width, Consumer<String> select, Runnable manage, Runnable beforeOpen) {
        this(x, y, width, width, select, manage, beforeOpen);
    }
    public PresetDropdownWidget(int x, int y, int width, int popupWidth, Consumer<String> select, Runnable manage, Runnable beforeOpen) {
        super(x, y, width, 20, Text.empty());
        this.popupWidth = Math.max(width, popupWidth);
        this.select = select; this.manage = manage; this.beforeOpen = beforeOpen;
    }
    public boolean isOpen() { return open; }
    public void close() { open = false; }
    private String current() { return ModelManager.getActiveModel() == null ? "" : ModelManager.getActiveModel().name; }
    private PresetMenuLayout layout() {
        return PresetMenuLayout.of(getY(), height, MinecraftClient.getInstance().getWindow().getScaledHeight(), names.size());
    }
    private void show() {
        beforeOpen.run();
        if (ColorPicker.openPicker != null) { ColorPicker.openPicker.open = false; ColorPicker.openPicker = null; }
        names = ModelManager.getAvailableModels();
        focusedRow = Math.max(0, names.indexOf(current()));
        first = Math.min(focusedRow, Math.max(0, names.size() - layout().rows()));
        open = true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(x, y)) return false;
        if (open) close(); else show();
        playDownSound(MinecraftClient.getInstance().getSoundManager());
        return true;
    }
    public boolean overlayClick(double x, double y, int button) {
        if (!open) return false;
        var menu = layout();
        int row = x >= getX() && x < getX() + popupWidth ? menu.rowAt(y) : -1;
        close();
        if (button == 0 && row >= 0) {
            if (row == menu.rows()) manage.run();
            else if (first + row < names.size()) select.accept(names.get(first + row));
        }
        return true;
    }
    public boolean overlayScroll(double amount) {
        if (!open) return false;
        first = Math.max(0, Math.min(Math.max(0, names.size() - layout().rows()), first - (int) Math.signum(amount)));
        return true;
    }
    public boolean overlayKey(int key) {
        if (!open) return false;
        if (key == 256 || key == 258) { close(); return true; }
        if (key == 264 || key == 265) {
            focusedRow = Math.floorMod(focusedRow + (key == 264 ? 1 : -1), names.size() + 1);
            if (focusedRow < names.size()) {
                if (focusedRow < first) first = focusedRow;
                if (focusedRow >= first + layout().rows()) first = focusedRow - layout().rows() + 1;
            }
        }
        if (key == 257 || key == 335 || key == 32) {
            close();
            if (focusedRow == names.size()) manage.run(); else select.accept(names.get(focusedRow));
        }
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (open) return overlayKey(key);
        if (active && isFocused() && (key == 257 || key == 335 || key == 32 || key == 264)) { show(); return true; }
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

    @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        if (now - lastDirtyCheck > 100_000_000L) { dirty = ModelManager.isPresetDirty(current()); lastDirtyCheck = now; }
        var font = MinecraftClient.getInstance().textRenderer;
        boolean hovered = this.isHovered();
        float targetAlpha = (active && !hovered) ? 0.15f : 0.35f;
        if (currentAlpha < targetAlpha) currentAlpha = Math.min(targetAlpha, currentAlpha + 0.05f);
        else if (currentAlpha > targetAlpha) currentAlpha = Math.max(targetAlpha, currentAlpha - 0.05f);
        float buttonAlpha = active ? this.alpha : this.alpha * 0.4f;
        int alpha = (int) (currentAlpha * buttonAlpha * 255);
        int textAlpha = (int) (buttonAlpha * 255);
        if (alpha > 0) fillRoundedRect(draw, getX(), getY(), width, height, (alpha << 24) | 0xFFFFFF);
        int textY = getY() + (height - 8) / 2;
        draw.drawTextWithShadow(font, font.trimToWidth(current(), Math.max(0, width - 34)), getX() + 6, textY, (textAlpha << 24) | 0xFFFFFF);
        if (dirty) draw.drawTextWithShadow(font, "*", getX() + width - 26, textY, (textAlpha << 24) | 0xFFFFD49A);
        draw.drawTextWithShadow(font, open ? "▴" : "▾", getX() + width - 14, textY, (textAlpha << 24) | 0xFFFFFF);
        setMessage(Text.literal(current() + (dirty ? " *" : "")));
    }
    public void renderOverlay(DrawContext draw, int mouseX, int mouseY) {
        if (!open) return;
        int width = popupWidth;
        var menu = layout();
        var font = MinecraftClient.getInstance().textRenderer;
        draw.getMatrices().push();
        try {
            draw.getMatrices().translate(0, 0, 600);
            fillRoundedRect(draw, getX(), menu.top(), width, menu.height(), 0xE6182230);
            int hover = mouseX >= getX() && mouseX < getX() + width ? menu.rowAt(mouseY) : -1;
            for (int row = 0; row <= menu.rows(); row++) {
                boolean management = row == menu.rows();
                int index = management ? names.size() : first + row;
                if (!management && index >= names.size()) continue;
                int y = menu.top() + row * menu.rowHeight();
                boolean highlighted = row == hover || focusedRow == index;
                int rowAlpha = (int) ((highlighted ? 0.35f : 0.15f) * 255);
                fillRoundedRect(draw, getX() + 2, y + 1, width - 4, menu.rowHeight() - 2, (rowAlpha << 24) | 0xFFFFFF);
                if (management) {
                    draw.drawTextWithShadow(font, font.trimToWidth(tr("manage").getString(), width - 12), getX() + 6, y + 6, 0xFFD7C2FF);
                } else {
                    String name = names.get(index);
                    String label = (name.equals(current()) ? "✓ " : "  ") + name;
                    draw.drawTextWithShadow(font, font.trimToWidth(label, width - 24), getX() + 6, y + 6, 0xFFFFFFFF);
                    if (ModelManager.isPresetDirty(name)) draw.drawTextWithShadow(font, "*", getX() + width - 14, y + 6, 0xFFFFD49A);
                }
            }
            if (names.size() > menu.rows()) {
                int track = menu.rows() * menu.rowHeight();
                int thumb = Math.max(8, track * menu.rows() / names.size());
                int top = menu.top() + first * (track - thumb) / Math.max(1, names.size() - menu.rows());
                draw.fill(getX() + width - 3, top, getX() + width - 1, top + thumb, 0xFFA8B7D0);
            }
        } finally { draw.getMatrices().pop(); }
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
    private static Text tr(String key) { return Text.translatable("text.magicaland.customize.preset." + key); }
}
