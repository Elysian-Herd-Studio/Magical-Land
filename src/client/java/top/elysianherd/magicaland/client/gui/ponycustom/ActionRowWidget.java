package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

final class ActionRowWidget extends ClickableWidget {
    record Action(Text label, BooleanSupplier enabled, BooleanSupplier selected, Runnable run) {}
    private final List<Action> actions;
    private final float[] currentAlpha;
    private int focusedAction;

    ActionRowWidget(int x, int width, Action... actions) {
        super(x, 0, width, 20, Text.empty());
        this.actions = List.of(actions);
        this.currentAlpha = new float[actions.length];
        java.util.Arrays.fill(this.currentAlpha, 0.15f);
    }
    static Action action(Text label, BooleanSupplier enabled, BooleanSupplier selected, Runnable run) {
        return new Action(label, enabled, selected, run);
    }
    static Action action(Text label, Runnable run) { return action(label, () -> true, () -> false, run); }
    private int left(int index) { return getX() + index * width / actions.size(); }
    private int right(int index) { return getX() + (index + 1) * width / actions.size() - 3; }
    private void invoke(int index) {
        Action action = actions.get(index);
        if (!action.enabled.getAsBoolean()) return;
        focusedAction = index;
        action.run.run();
        playDownSound(MinecraftClient.getInstance().getSoundManager());
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(x, y)) return false;
        for (int i = 0; i < actions.size(); i++) if (x >= left(i) && x < right(i)) { invoke(i); return true; }
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        if (key == 262 || key == 263) { focusedAction = Math.floorMod(focusedAction + (key == 262 ? 1 : -1), actions.size()); return true; }
        if (key == 257 || key == 335 || key == 32) { invoke(focusedAction); return true; }
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
        var font = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            boolean enabled = active && action.enabled.getAsBoolean();
            boolean selected = action.selected.getAsBoolean();
            boolean hover = mouseX >= left(i) && mouseX < right(i) && mouseY >= getY() && mouseY < getY() + height;
            float targetAlpha = (!enabled || hover || selected) ? 0.35f : 0.15f;
            if (currentAlpha[i] < targetAlpha) currentAlpha[i] = Math.min(targetAlpha, currentAlpha[i] + 0.05f);
            else if (currentAlpha[i] > targetAlpha) currentAlpha[i] = Math.max(targetAlpha, currentAlpha[i] - 0.05f);
            float buttonAlpha = enabled ? this.alpha : this.alpha * 0.4f;
            int bgAlpha = (int) (currentAlpha[i] * buttonAlpha * 255);
            int textAlpha = (int) (buttonAlpha * 255);
            if (bgAlpha > 0) fillRoundedRect(draw, left(i), getY(), right(i) - left(i), height, (bgAlpha << 24) | 0xFFFFFF);
            draw.drawCenteredTextWithShadow(font, font.trimToWidth(action.label.getString(), Math.max(0, right(i) - left(i) - 6)),
                    (left(i) + right(i)) / 2, getY() + 6, (textAlpha << 24) | 0xFFFFFF);
        }
        setMessage(actions.get(focusedAction).label);
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
