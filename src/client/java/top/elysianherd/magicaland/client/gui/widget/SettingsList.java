package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.Collections;
import java.util.List;

public class SettingsList extends ElementListWidget<SettingsList.Entry> {
    private long lastInteractionTime = 0;
    private double lastScrollAmount = 0.0;
    private long lastRenderTime = 0;
    private float scrollbarAlpha = 0.0f;
    private double targetScrollAmount = 0.0;
    private boolean isDraggingScrollbar = false;
    private final int originalTop;
    private final int originalBottom;

    public SettingsList(MinecraftClient minecraftClient, int width, int height, int top, int bottom,
            int itemHeight) {
        super(minecraftClient, width, height, top, bottom, itemHeight);
        this.originalTop = top;
        this.originalBottom = bottom;
        this.setRenderBackground(false);
        this.setRenderHorizontalShadows(false);
    }

    public void centerIfShort() {
        int totalHeight = this.getMaxPosition();
        int availableHeight = this.originalBottom - this.originalTop;
        if (totalHeight < availableHeight) {
            int padding = (availableHeight - totalHeight) / 2;
            this.top = this.originalTop + padding;
            this.bottom = this.originalBottom - padding;
        } else {
            this.top = this.originalTop;
            this.bottom = this.originalBottom;
        }
    }

    @Override
    public int getMaxScroll() {
        return Math.max(0, this.getMaxPosition() - (this.originalBottom - this.originalTop - 4));
    }

    public enum Alignment {
        CENTER,
        RIGHT
    }

    public void addWidget(ClickableWidget widget) {
        this.addEntry(new Entry(widget, this, Alignment.CENTER));
    }

    public void addWidget(ClickableWidget widget, Alignment alignment) {
        this.addEntry(new Entry(widget, this, alignment));
    }

    private int rowHeight(int index) {
        return Math.max(itemHeight, children().get(index).widget.getHeight() + 4);
    }

    private int rowOffset(int index) {
        int offset = headerHeight;
        for (int i = 0; i < index; i++) offset += rowHeight(i);
        return offset;
    }

    @Override
    protected int getMaxPosition() { return rowOffset(children().size()); }

    @Override
    protected int getRowTop(int index) { return top + 4 - (int) getScrollAmount() + rowOffset(index); }

    @Override
    protected int getRowBottom(int index) { return getRowTop(index) + rowHeight(index); }

    public void ensureWidgetAreaVisible(ClickableWidget widget, int localTop, int localBottom) {
        for (int i = 0; i < children().size(); i++) if (children().get(i).widget == widget) {
            int rowY = getRowTop(i);
            double target = getScrollAmount();
            if (rowY + localTop < top + 4) target += rowY + localTop - top - 4;
            else if (rowY + localBottom > bottom - 4) target += rowY + localBottom - bottom + 4;
            restoreScrollAmount(target);
            return;
        }
    }

    @Override
    protected void ensureVisible(Entry entry) {
        // 大型缩略图池自行定位选中卡片，不把整池反复拉回顶部。
        if (entry.widget.getHeight() <= itemHeight)
            ensureWidgetAreaVisible(entry.widget, 0, entry.widget.getHeight());
    }

    @Override
    protected void centerScrollOn(Entry entry) {
        int index = children().indexOf(entry);
        if (index >= 0) restoreScrollAmount(rowOffset(index) - (bottom - top - rowHeight(index)) / 2.0);
    }

    @Override
    protected void renderList(DrawContext context, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < children().size(); i++) {
            int rowY = getRowTop(i);
            if (rowY >= bottom || rowY + rowHeight(i) <= top) continue;
            children().get(i).render(context, i, rowY, getRowLeft(), getRowWidth(), rowHeight(i) - 4,
                    mouseX, mouseY, isMouseOver(mouseX, mouseY) && mouseY >= rowY && mouseY < rowY + rowHeight(i), delta);
        }
    }

    public void restoreScrollAmount(double amount) {
        this.setScrollAmount(amount);
        this.targetScrollAmount = this.getScrollAmount();
        this.lastScrollAmount = this.targetScrollAmount;
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.left + this.width - 6;
    }

    private void renderRoundedScrollbar(DrawContext context, int x, int y, int width, int height, float alpha) {
        if (alpha <= 0.01f)
            return;

        int alphaInt = (int) (alpha * 255.0f);
        int color = (alphaInt << 24) | 0x00C0C0C0;

        int x1 = x;
        int y1 = y;
        int x2 = x + width;
        int y2 = y + height;

        context.fill(x1 + 1, y1, x2 - 1, y1 + 1, color);
        context.fill(x1, y1 + 1, x2, y2 - 1, color);
        context.fill(x1 + 1, y2 - 1, x2 - 1, y2, color);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.targetScrollAmount = net.minecraft.util.math.MathHelper.clamp(this.targetScrollAmount, 0.0,
                this.getMaxScroll());
        if (Math.abs(this.targetScrollAmount - this.getScrollAmount()) > 0.1) {
            double newScroll = net.minecraft.util.math.MathHelper.lerp(0.3, this.getScrollAmount(),
                    this.targetScrollAmount);
            this.setScrollAmount(newScroll);
        } else {
            this.setScrollAmount(this.targetScrollAmount);
        }

        context.getMatrices().push();
        this.renderList(context, mouseX, mouseY, delta);
        context.getMatrices().pop();

        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            long currentTime = System.currentTimeMillis();
            long deltaMs = currentTime - (this.lastRenderTime == 0 ? currentTime : this.lastRenderTime);
            this.lastRenderTime = currentTime;

            int i = this.getScrollbarPositionX();

            boolean isHovering = mouseX >= i - 20 && mouseX <= i + 26 && mouseY >= this.top && mouseY <= this.bottom;
            boolean isScrolling = Math.abs(this.getScrollAmount() - this.lastScrollAmount) > 0.01;

            if (isHovering || isScrolling) {
                this.lastInteractionTime = currentTime;
            }
            this.lastScrollAmount = this.getScrollAmount();

            long timeSinceLastInteraction = currentTime - this.lastInteractionTime;

            if (timeSinceLastInteraction < 1000) {
                this.scrollbarAlpha = Math.min(1.0f, this.scrollbarAlpha + deltaMs / 200.0f);
            } else {
                this.scrollbarAlpha = Math.max(0.0f, this.scrollbarAlpha - deltaMs / 500.0f);
            }

            if (this.scrollbarAlpha > 0.0f) {
                int j = (int) ((float) (this.bottom - this.top) * (float) (this.bottom - this.top)
                        / (float) this.getMaxPosition());
                j = net.minecraft.util.math.MathHelper.clamp(j, 32, this.bottom - this.top - 8);
                int k = (int) this.getScrollAmount() * (this.bottom - this.top - j) / maxScroll + this.top;
                if (k < this.top) {
                    k = this.top;
                }
                this.renderRoundedScrollbar(context, i, k, 6, j, this.scrollbarAlpha);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.updateScrollingState(mouseX, mouseY, button);
        if (this.isMouseOver(mouseX, mouseY)) {
            int i = this.getScrollbarPositionX();
            if (button == 0 && mouseX >= (double) i && mouseX <= (double) (i + 6)) {
                this.isDraggingScrollbar = true;
                return true;
            }
        }
        if (!isMouseOver(mouseX, mouseY)) return false;
        for (int index = 0; index < children().size(); index++) {
            Entry entry = children().get(index);
            int rowY = getRowTop(index);
            if (mouseY < rowY || mouseY >= rowY + rowHeight(index)) continue;
            entry.position(getRowLeft(), rowY, getRowWidth());
            if (entry.mouseClicked(mouseX, mouseY, button)) {
                setFocused(entry);
                if (button == 0) setDragging(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingScrollbar = false;
            setDragging(false);
        }
        Entry entry = getFocused();
        return entry != null && entry.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY) || this.getMaxScroll() <= 0) {
            return false;
        }
        this.targetScrollAmount = net.minecraft.util.math.MathHelper
                .clamp(this.targetScrollAmount - amount * this.itemHeight, 0.0, this.getMaxScroll());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDraggingScrollbar) {
            int maxScroll = this.getMaxScroll();
            if (maxScroll > 0) {
                int j = (int) ((float) (this.bottom - this.top) * (float) (this.bottom - this.top)
                        / (float) this.getMaxPosition());
                j = net.minecraft.util.math.MathHelper.clamp(j, 32, this.bottom - this.top - 8);
                double d = Math.max(1.0, maxScroll / (double) (this.bottom - this.top - j));
                this.targetScrollAmount = net.minecraft.util.math.MathHelper.clamp(this.targetScrollAmount + deltaY * d,
                        0.0, maxScroll);
                this.setScrollAmount(this.targetScrollAmount);
                return true;
            }
        }
        
        Element focused = this.getFocused();
        if (focused instanceof Entry entry) {
            if (entry.widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        } else if (focused != null && focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        boolean result = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        this.targetScrollAmount = this.getScrollAmount();
        return result;
    }

    public static class Entry extends ElementListWidget.Entry<Entry> {
        public final ClickableWidget widget;
        private final SettingsList parent;
        private final Alignment alignment;

        public Entry(ClickableWidget widget, SettingsList parent) {
            this(widget, parent, Alignment.CENTER);
        }

        public Entry(ClickableWidget widget, SettingsList parent, Alignment alignment) {
            this.widget = widget;
            this.parent = parent;
            this.alignment = alignment;
        }

        @Override
        public List<? extends Element> children() {
            return Collections.singletonList(this.widget);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return Collections.singletonList(this.widget);
        }

        private void position(int x, int y, int entryWidth) {
            widget.setX(alignment == Alignment.RIGHT ? x + entryWidth - widget.getWidth()
                    : x + (entryWidth - widget.getWidth()) / 2);
            widget.setY(y);
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return parent.isMouseOver(x, y) && widget.isMouseOver(x, y);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                int mouseY, boolean hovered, float tickDelta) {
            position(x, y, entryWidth);

            int widgetTop = y;
            int widgetBottom = y + this.widget.getHeight();

            int fadeDistance = 15;
            float alpha = 1.0f;

            if (widget.getHeight() > parent.itemHeight) {
                alpha = 1;
            } else if (widgetTop < this.parent.top) {
                alpha = Math.max(0.0f, 1.0f - (float) (this.parent.top - widgetTop) / fadeDistance);
            } else if (widgetBottom > this.parent.bottom) {
                alpha = Math.max(0.0f, 1.0f - (float) (widgetBottom - this.parent.bottom) / fadeDistance);
            }

            float finalAlpha = alpha < 0.01f ? 0.0f : alpha;
            this.widget.setAlpha(finalAlpha);

            this.widget.render(context, mouseX, mouseY, tickDelta);
        }
    }
}
