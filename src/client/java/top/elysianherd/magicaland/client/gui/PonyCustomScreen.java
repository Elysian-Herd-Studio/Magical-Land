package top.elysianherd.magicaland.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;

public final class PonyCustomScreen extends Screen {
    private final Screen parent;
    private final PonyCustom ponyCustom = new PonyCustom();
    private boolean editingAvailable;
    private boolean editingStarted;
    private boolean saveError;
    private boolean confirmingDiscard;
    private boolean editorFinished;
    private CustomButton saveButton;
    private boolean dirtyPreview;
    private long lastDirtyCheck;

    public PonyCustomScreen(Screen parent) {
        super(Text.translatable("text.magicaland.console.tab.pony_custom"));
        this.parent = parent;
        ponyCustom.onEnter();
    }

    public void addConsoleWidget(net.minecraft.client.gui.widget.ClickableWidget widget) { addDrawableChild(widget); }

    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable> void addConsoleElement(T element) {
        addDrawableChild(element);
    }

    public void reinitScreen() {
        if (ColorPicker.openPicker != null) {
            ColorPicker.openPicker.open = false;
            ColorPicker.openPicker = null;
        }
        clearChildren();
        init();
    }

    @Override
    protected void init() {
        super.init();
        if (!editingStarted) {
            editingStarted = true;
            editingAvailable = !ModelManager.isEditing() && ModelManager.beginEditing();
        }
        if (ColorPicker.openPicker != null) {
            ColorPicker.openPicker.open = false;
            ColorPicker.openPicker = null;
        }
        saveButton = addDrawableChild(new CustomButton(width - 166, 6, 100, 20,
                editText("save"), false, button -> saveAndApply()));
        addDrawableChild(new CustomButton(width - 62, 6, 54, 20,
                editText("close"), false, button -> close()));
        if (editingAvailable) ponyCustom.init(this, 8, 31, width - 16, height - 39);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        long now = System.nanoTime();
        if (now - lastDirtyCheck > 100_000_000L) {
            dirtyPreview = editingAvailable && ModelManager.isDirty();
            lastDirtyCheck = now;
        }
        saveButton.active = dirtyPreview && !ponyCustom.hasPendingTextEdit();
        saveButton.setMessage(editText("save").copy().append(dirtyPreview ? " *" : ""));
        context.drawTextWithShadow(textRenderer, title, 8, 10, 0xFFFFFF);
        if (editingAvailable) ponyCustom.render(context, 8, 31, width - 16, height - 39, mouseX, mouseY, delta, 1);
        else {
            var lines = textRenderer.wrapLines(editText("begin_error"), Math.max(32, width - 32));
            int top = (height - lines.size() * textRenderer.fontHeight) / 2;
            for (int i = 0; i < lines.size(); i++)
                context.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2,
                        top + i * textRenderer.fontHeight, 0xFFFF9999);
        }
        for (var element : children())
            if (element instanceof net.minecraft.client.gui.widget.ClickableWidget widget)
                widget.render(context, mouseX, mouseY, delta);
        if (ColorPicker.openPicker != null && ColorPicker.openPicker.open) {
            ColorPicker.openPicker.renderOverlay(context, mouseX, mouseY);
            setTooltip(java.util.List.of());
        }
        renderSaveError(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ColorPicker.openPicker != null && ColorPicker.openPicker.open) {
            if (ColorPicker.openPicker.isMouseOver(mouseX, mouseY)) return ColorPicker.openPicker.mouseClicked(mouseX, mouseY, button);
            ColorPicker.openPicker.open = false;
            ColorPicker.openPicker = null;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return editingAvailable && ponyCustom.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && ColorPicker.openPicker != null && ColorPicker.openPicker.open) {
            ColorPicker.openPicker.open = false;
            ColorPicker.openPicker = null;
            return true;
        }
        if (editingAvailable && ponyCustom.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editingAvailable && ponyCustom.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (ColorPicker.openPicker != null && ColorPicker.openPicker.open && button == 0
                && ColorPicker.openPicker.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (editingAvailable && ponyCustom.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void tick() {
        super.tick();
        for (net.minecraft.client.gui.Element element : children())
            if (element instanceof TextFieldWidget textField) textField.tick();
    }

    @Override
    public void close() {
        if (editingAvailable && ModelManager.isDirty() && !editorFinished) {
            confirmingDiscard = true;
            boolean[] handled = {false};
            client.setScreen(new ConfirmScreen(discard -> {
                handled[0] = true;
                confirmingDiscard = false;
                if (discard) finishEditor(false);
                else client.setScreen(this);
            }, editText("discard_title"), editText("discard_body"), editText("discard"), editText("continue")) {
                @Override public void removed() {
                    super.removed();
                    if (!handled[0]) {
                        confirmingDiscard = false;
                        cleanupEditor(false);
                    }
                }
            });
            return;
        }
        finishEditor(false);
    }

    private void saveAndApply() {
        if (!editingAvailable || ponyCustom.hasPendingTextEdit()) return;
        if (!ModelManager.commitEditing()) {
            saveError = true;
            return;
        }
        finishEditor(true);
        ClientNetworkHandler.publishSavedModel();
    }

    private void finishEditor(boolean saved) {
        cleanupEditor(saved);
        client.setScreen(parent);
    }

    private void cleanupEditor(boolean saved) {
        if (editorFinished) return;
        if (!saved && editingAvailable) ModelManager.discardEditing();
        editorFinished = true;
        if (ColorPicker.openPicker != null) {
            ColorPicker.openPicker.open = false;
            ColorPicker.openPicker = null;
        }
        ponyCustom.onExit();
        ponyCustom.endEditingSession();
        clearChildren();
    }

    @Override
    public void removed() {
        super.removed();
        if (!confirmingDiscard && !editorFinished) cleanupEditor(false);
    }

    private static Text editText(String key) { return Text.translatable("text.magicaland.customize.editor." + key); }

    private void renderSaveError(DrawContext context) {
        if (!saveError) return;
        var lines = textRenderer.wrapLines(editText("save_error"), width - 28);
        int top = height - 12 - lines.size() * textRenderer.fontHeight;
        context.fill(6, top, width - 6, height - 4, 0xEF3B1721);
        for (int i = 0; i < lines.size(); i++)
            context.drawTextWithShadow(textRenderer, lines.get(i), 14, top + 4 + i * textRenderer.fontHeight, 0xFFFFCCCC);
    }
}
