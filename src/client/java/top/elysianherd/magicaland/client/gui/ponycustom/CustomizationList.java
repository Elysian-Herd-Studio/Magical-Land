package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

public final class CustomizationList extends SettingsList {
    private final CustomizationLayout.Rect bounds;

    public CustomizationList(MinecraftClient client, CustomizationLayout.Rect bounds) {
        super(client, bounds.width(), client.getWindow().getScaledHeight(), bounds.y() + 4, bounds.bottom() - 4, 24);
        this.bounds = bounds;
        setLeftPos(bounds.x());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Entry entry : children()) {
            if (entry.widget instanceof ColorPicker picker) picker.setExternalOverlay(true);
            if (entry.widget instanceof StyleGridWidget grid) grid.setViewport(bounds.y(), bounds.bottom());
            if (entry.widget instanceof PixelCanvasWidget canvas) canvas.setViewport(bounds.y(), bounds.bottom());
        }
        context.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        try {
            super.render(context, mouseX, mouseY, delta);
        } finally {
            context.disableScissor();
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!bounds.contains(x, y) || ColorPicker.openPicker != null) return false;
        return super.mouseScrolled(x, y, amount);
    }
}
