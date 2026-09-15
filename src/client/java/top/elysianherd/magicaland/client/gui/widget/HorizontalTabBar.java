package top.elysianherd.magicaland.client.gui.widget;

import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class HorizontalTabBar {
    private final Text[] labels;
    private int columns;
    private final int gap;
    private final int rowHeight;
    private int x;
    private int y;
    private int cellWidth;
    private int selected;
    private float indicatorX = -1;
    private float indicatorY = -1;
    private float targetX = -1;
    private float targetY = -1;

    public HorizontalTabBar(Text[] labels, int columns, int gap, int rowHeight) {
        this.labels = labels.clone();
        this.columns = Math.max(1, columns);
        this.gap = Math.max(0, gap);
        this.rowHeight = Math.max(1, rowHeight);
    }

    public void setColumns(int columns) {
        this.columns = Math.max(1, columns);
    }

    public void init(int x, int y, int cellWidth, int selected) {
        this.x = x;
        this.y = y;
        this.cellWidth = Math.max(1, cellWidth);
        setSelected(selected);
    }

    public void setSelected(int selected) {
        this.selected = Math.max(0, Math.min(labels.length - 1, selected));
    }

    public void resetIndicator() {
        indicatorX = -1;
        indicatorY = -1;
        targetX = -1;
        targetY = -1;
    }

    public void render(DrawContext context, int mouseX, int mouseY, boolean enabled) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < labels.length; i++) {
            int left = left(i);
            int top = top(i);
            boolean hovered = enabled && mouseX >= left && mouseX < left + cellWidth
                    && mouseY >= top && mouseY < top + rowHeight;
            int color = !enabled ? 0xFF555555 : i == selected ? 0xFFFFFFFF : hovered ? 0xFFCCCCCC : 0xFF777777;
            int textX = left + (cellWidth - textRenderer.getWidth(labels[i])) / 2;
            context.drawText(textRenderer, labels[i], textX, top + 6, color, false);
        }

        int selectedLeft = left(selected);
        int selectedTop = top(selected);
        targetX = selectedLeft + cellWidth / 2f;
        targetY = selectedTop + 20;
        if (indicatorX < 0) {
            indicatorX = targetX;
            indicatorY = targetY;
        } else {
            indicatorX += (targetX - indicatorX) * 0.3f;
            indicatorY += (targetY - indicatorY) * 0.3f;
        }
        int width = textRenderer.getWidth(labels[selected]) + 8;
        int centerX = Math.round(indicatorX);
        int lineY = Math.round(indicatorY);
        context.fill(centerX - width / 2, lineY, centerX + width / 2, lineY + 2, 0xFFFFFFFF);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean enabled, IntConsumer onSelect) {
        if (button != 0 || !enabled) return false;
        int index = indexAt(mouseX, mouseY);
        if (index < 0) return false;
        setSelected(index);
        onSelect.accept(index);
        return true;
    }

    private int indexAt(double mouseX, double mouseY) {
        for (int i = 0; i < labels.length; i++) {
            int left = left(i);
            int top = top(i);
            if (mouseX >= left && mouseX < left + cellWidth
                    && mouseY >= top && mouseY < top + rowHeight) return i;
        }
        return -1;
    }

    private int left(int index) { return x + index % columns * (cellWidth + gap); }
    private int top(int index) { return y + index / columns * rowHeight; }

}
