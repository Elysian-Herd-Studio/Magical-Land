package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.gui.widget.HorizontalTabBar;
import top.elysianherd.magicaland.client.render.ManePalette.Part;

public final class ManePartTabs extends ClickableWidget {
    private final HorizontalTabBar bar;
    private final Part selected;
    private final Consumer<Part> onSelect;

    public ManePartTabs(int x, int width, HorizontalTabBar bar, Part selected, Consumer<Part> onSelect) {
        super(x, 0, width, 24, label(selected));
        this.bar = bar;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    static Text label(Part part) {
        return Text.translatable("text.magicaland.customize.styles.part." + part.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
        bar.init(getX(), getY(), Math.max(1, width / 3), selected.ordinal());
        bar.render(draw, mouseX, mouseY, active);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible || button != 0 || !clicked(x, y)) return false;
        boolean handled = bar.mouseClicked(x, y, button, true, index -> onSelect.accept(Part.values()[index]));
        if (handled) playDownSound(MinecraftClient.getInstance().getSoundManager());
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        if (key != 262 && key != 263 && key != 257 && key != 335 && key != 32) return false;
        int next = key == 262 ? Math.min(2, selected.ordinal() + 1)
                : key == 263 ? Math.max(0, selected.ordinal() - 1) : selected.ordinal();
        onSelect.accept(Part.values()[next]);
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, label(selected));
        builder.put(NarrationPart.USAGE, Text.translatable("text.magicaland.customize.styles.part_hint"));
    }
}
