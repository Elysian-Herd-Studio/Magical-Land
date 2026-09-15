package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.function.Consumer;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;

public final class PartColorPicker extends ColorPicker {
    private final PonyCustomPageContext context;
    private final PonyStylePart part;

    public PartColorPicker(PonyCustomPageContext context, PonyStylePart part, int x, int y, int width, int height,
            Text label, String initialColor, Consumer<String> onColorChanged) {
        super(x, y, width, height, label, initialColor, onColorChanged);
        this.context = context;
        this.part = part;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (handled) context.focusPart(part);
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        if (handled) context.focusPart(part);
        return handled;
    }
}
