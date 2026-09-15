package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.SectionLabel;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;
import static top.elysianherd.magicaland.client.gui.ponycustom.ActionRowWidget.action;

public final class CutieMarkPage implements PonyCustomPage {
    private final Map<ModelConfig, PixelCanvasHistory> histories = new IdentityHashMap<>();
    private Object session;
    private PixelCanvasHistory editor;
    private ModelConfig selected;
    private boolean confirmingLink;
    private PonyCustomPageContext context;

    @Override public void onEnter() { confirmingLink = false; }
    @Override public void onLeave() { if (editor != null) editor.finishStroke(); confirmingLink = false; }
    public void releaseSession() {
        onLeave(); histories.clear(); session = null; editor = null; selected = null; context = null;
    }
    @Override public boolean isEditingPreset() { return confirmingLink; }
    @Override public boolean keyPressed(int key) {
        if (!confirmingLink) return false;
        if (key == 256) { confirmingLink = false; context.refreshKeepingScroll(); return true; }
        if (key == 257 || key == 335) { confirmLink(); return true; }
        return false;
    }
    private void confirmLink() {
        if (ModelManager.getActiveModel() == selected && editor != null) editor.setLinked(true);
        confirmingLink = false;
        context.refreshKeepingScroll();
    }
    @Override public void build(PonyCustomPageContext context, SettingsList list) {
        this.context = context;
        if (session != ModelManager.editingSessionIdentity()) {
            histories.clear(); session = ModelManager.editingSessionIdentity(); editor = null;
        }
        selected = ModelManager.getActiveModel();
        if (selected == null) return;
        ModelConfig config = selected;
        editor = histories.computeIfAbsent(config, model -> new PixelCanvasHistory(new PixelCanvasHistory.Target() {
            public int[] pixels(boolean left) { return model.cutieMarkPixels(left); }
            public void pixels(boolean left, int[] pixels) { model.setCutieMarkPixels(left, pixels); }
            public boolean linked() { return model.cutieMarkLinked; }
            public void linked(boolean linked, boolean sourceLeft) { model.setCutieMarkLinked(linked, sourceLeft); }
        }));
        context.focusCutieMark(editor.left());
        int x = context.getX() + 10, width = context.getControlWidth();
        if (confirmingLink) {
            Text prompt = Text.translatable("text.magicaland.customize.mark.link_confirm", tr(editor.left() ? "left" : "right"));
            int textHeight = MinecraftClient.getInstance().textRenderer.wrapLines(prompt, width - 12).size() * 11 + 8;
            list.addWidget(new SectionLabel(x, 0, width, textHeight, prompt) {
                @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
                    PonyCustomPageHelper.drawWrapped(draw, getMessage(), getX() + 6, getY() + 4, getWidth() - 12, 0xFFFFC4A5);
                }
            }, SettingsList.Alignment.RIGHT);
            list.addWidget(new ActionRowWidget(x, width,
                    action(tr("replace_other"), this::confirmLink),
                    action(tr("cancel"), () -> { confirmingLink = false; context.refreshKeepingScroll(); })), SettingsList.Alignment.RIGHT);
            return;
        }
        list.addWidget(new ActionRowWidget(x, width,
                action(tr("left"), () -> true, editor::left, () -> { editor.side(true); context.focusCutieMark(true); }),
                action(tr("right"), () -> true, () -> !editor.left(), () -> { editor.side(false); context.focusCutieMark(false); }),
                action(tr("linked"), () -> true, editor::linked, () -> {
                    editor.finishStroke();
                    if (editor.linked()) editor.setLinked(false);
                    else { confirmingLink = true; context.refreshKeepingScroll(); }
                })), SettingsList.Alignment.RIGHT);
        list.addWidget(new ActionRowWidget(x, width,
                action(tr("brush"), () -> true, () -> editor.tool() == PixelCanvasHistory.Tool.BRUSH, () -> editor.tool(PixelCanvasHistory.Tool.BRUSH)),
                action(tr("eraser"), () -> true, () -> editor.tool() == PixelCanvasHistory.Tool.ERASER, () -> editor.tool(PixelCanvasHistory.Tool.ERASER)),
                action(tr("picker"), () -> true, () -> editor.tool() == PixelCanvasHistory.Tool.PICKER, () -> editor.tool(PixelCanvasHistory.Tool.PICKER))), SettingsList.Alignment.RIGHT);
        int mirrorWidth = Math.max(40, Math.min(width / 3, width - 96));
        ColorPicker picker = new ColorPicker(x + mirrorWidth + 4, 0, width - mirrorWidth - 4, 20, tr("color"), String.format("#%08X", editor.color()), color -> {
            try { editor.color((int) Long.parseLong(color.replace("#", ""), 16)); } catch (NumberFormatException ignored) {}
        });
        picker.setExternalOverlay(true);
        list.addWidget(new MirrorColorRow(x, width, mirrorWidth, picker, editor::mirror), SettingsList.Alignment.RIGHT);
        PixelCanvasWidget canvas = new PixelCanvasWidget(x, width, editor,
                () -> ModelManager.isEditing() && ModelManager.getActiveModel() == config,
                color -> picker.setColorSilent(String.format("#%08X", color)));
        list.addWidget(canvas, SettingsList.Alignment.RIGHT);
    }
    private static Text tr(String key) { return Text.translatable("text.magicaland.customize.mark." + key); }
}
