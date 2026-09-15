package top.elysianherd.magicaland.client.gui;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.elysianherd.magicaland.client.gui.ponycustom.PonyPreviewRenderer;
import top.elysianherd.magicaland.client.gui.ponycustom.PonyStyleThumbnails;
import top.elysianherd.magicaland.client.gui.ponycustom.PreviewCamera;
import top.elysianherd.magicaland.client.gui.ponycustom.PreviewGeometryBounds;
import top.elysianherd.magicaland.client.gui.ponymanager.ModelGridWidget;
import top.elysianherd.magicaland.client.gui.ponymanager.ModelGridWidget.ModelEntry;
import top.elysianherd.magicaland.client.gui.ponymanager.PonyManagerLayout;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.ViewCube;

public final class PonyManagerScreen extends Screen implements ViewCube.RotationTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(PonyManagerScreen.class);
    private final Screen parent;
    private final PreviewCamera camera = new PreviewCamera();
    private PonyManagerLayout layout;
    private PonyPreviewRenderer renderer;
    private ModelGridWidget modelGrid;
    private List<ModelEntry> models;
    private CustomButton duplicateButton, renameButton, deleteButton, customizeButton;
    private boolean operationError;
    private boolean previewError;
    private boolean draggingPreview;
    private boolean refreshRequested;
    private boolean revealSelection = true;
    private double listScroll;
    private long lastPreviewFrame;

    public PonyManagerScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (modelGrid != null) listScroll = modelGrid.getScrollAmount();
        layout = PonyManagerLayout.of(width, height);
        draggingPreview = false;
        if (renderer == null) renderer = new PonyPreviewRenderer();
        if (models == null) {
            ModelManager.refreshModelList();
            models = ModelManager.getAvailableModels().stream().sorted(Comparator.naturalOrder())
                    .map(name -> new ModelEntry(name, ModelManager.getModelPreview(name))).toList();
        }
        modelGrid = addDrawableChild(new ModelGridWidget(layout.grid(), models, this::activeName, this::selectModel));
        modelGrid.active = !ModelManager.isEditing();
        if (!revealSelection) modelGrid.restoreScrollAmount(listScroll);
        revealSelection = false;
        addDrawableChild(new CustomButton(width - 70, 6, 62, 20,
                Text.translatable("text.magicaland.config.button.back"), false, button -> close()));
        addDrawableChild(new CustomButton(width - 154, 6, 78, 20,
                Text.translatable("text.magicaland.mglskin.cloud_title"), false,
                button -> client.setScreen(new MglCloudPresetScreen(this))));
        CustomButton createButton = addButton(layout.create(), text("create"), button -> {
            if (!ModelManager.isEditing()) client.setScreen(new ModelNameScreen(this, true));
        });
        createButton.active = !ModelManager.isEditing();
        duplicateButton = addButton(layout.duplicate(), text("duplicate"), button -> {
            if (!canEditModel()) return;
            operationError = !ModelManager.duplicateActiveModel(
                    Text.translatable("text.magicaland.customize.preset.copy_suffix").getString());
            if (!operationError) refreshModels();
        });
        renameButton = addButton(layout.rename(), text("rename"), button -> {
            if (canEditModel()) client.setScreen(new ModelNameScreen(this, false));
        });
        deleteButton = addButton(layout.delete(), text("delete"), button -> confirmDelete());
        customizeButton = addButton(layout.customize(),
                Text.translatable("text.magicaland.console.tab.pony_custom"), button -> {
                    if (canEditModel()) client.setScreen(new PonyCustomScreen(this));
                });
        if (layout.model().width() >= 170 && layout.model().height() >= 140)
            addDrawableChild(new ViewCube(layout.model().right() - 46, layout.model().y() + 2, 44, 44, this));
        updateButtons();
    }

    private CustomButton addButton(Rect bounds, Text label, Consumer<CustomButton> onPress) {
        return addDrawableChild(new CustomButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                label, false, onPress));
    }

    private String activeName() {
        ModelConfig model = ModelManager.getActiveModel();
        return model == null ? "" : model.name;
    }

    private boolean canEditModel() { return !ModelManager.isEditing() && ModelManager.getActiveModel() != null; }

    private void updateButtons() {
        boolean available = canEditModel();
        duplicateButton.active = available;
        renameButton.active = available;
        deleteButton.active = available && models.size() > 1;
        customizeButton.active = available;
    }

    private void selectModel(String name) {
        if (ModelManager.isEditing()) return;
        operationError = false;
        if (name.equals(activeName())) return;
        if (!ModelManager.loadModel(name)) {
            operationError = true;
            return;
        }
        previewError = false;
        updateButtons();
    }

    private void refreshModels() {
        refreshRequested = true;
        revealSelection = true;
    }

    private void applyPendingRefresh() {
        if (!refreshRequested) return;
        refreshRequested = false;
        models = null;
        revealSelection = true;
        previewError = false;
        PonyStyleThumbnails.clearModels();
        setFocused(null);
        clearChildren();
        init();
    }

    private void confirmDelete() {
        if (!canEditModel() || models.size() <= 1) return;
        String target = activeName();
        operationError = false;
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                operationError = !canEditModel() || !target.equals(activeName()) || !ModelManager.deleteModel(target);
                revealSelection = !operationError;
            }
            client.setScreen(this);
        }, text("delete_title"), text("delete_confirm", target), text("delete"),
                Text.translatable("text.magicaland.config.button.cancel")));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        applyPendingRefresh();
        renderBackground(context);
        updateButtons();
        Rect panel = layout.preview();
        context.drawTextWithShadow(textRenderer, title, 8, 10, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, text("models").copy().append(" (" + models.size() + ")"),
                layout.grid().x() + 3, 40, 0xFFFFFFFF);
        ModelConfig active = ModelManager.getActiveModel();
        Text current = active == null ? text("none") : Text.literal(active.name);
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(current.getString(), panel.width() - 12),
                panel.x() + panel.width() / 2, panel.y() + 8, 0xFFFFFFFF);
        renderPreview(context, active, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        if (operationError) renderError(context,
                Text.translatable("text.magicaland.customize.preset.operation_error"));
        else if (previewError) renderError(context, text("preview_error"));
    }

    private void renderPreview(DrawContext context, ModelConfig model, int mouseX, int mouseY, float delta) {
        if (model == null || previewError) return;
        try {
            ModelConfig config = AppearanceAnatomy.apply(client.player == null ? null : client.player.getUuid(), model);
            var box = PreviewGeometryBounds.framingBounds(config, null);
            Rect area = layout.model();
            long now = System.nanoTime();
            double seconds = lastPreviewFrame == 0 ? 1.0 / 60 : (now - lastPreviewFrame) / 1.0e9;
            lastPreviewFrame = now;
            var pose = camera.update(new PreviewCamera.Box(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()), area.width(), area.height(), seconds, false);
            renderer.renderPreview(context, config, area, pose, now, delta, mouseX, mouseY);
        } catch (RuntimeException failure) {
            previewError = true;
            LOGGER.warn("Unable to render pony management preview", failure);
        }
    }

    private void renderError(DrawContext context, Text error) {
        Rect panel = layout.preview();
        var lines = textRenderer.wrapLines(error, Math.max(20, panel.width() - 16));
        int top = panel.bottom() - lines.size() * textRenderer.fontHeight - 12;
        context.fill(panel.x() + 4, top - 4, panel.right() - 4, panel.bottom() - 4, 0xEF3B1721);
        for (int i = 0; i < lines.size(); i++)
            context.drawTextWithShadow(textRenderer, lines.get(i), panel.x() + 8,
                    top + i * textRenderer.fontHeight, 0xFFFFCCCC);
    }

    @Override public float getPreviewYaw() { return camera.pose() == null ? 155 : camera.pose().yaw(); }
    @Override public float getPreviewPitch() { return camera.pose() == null ? -10 : camera.pose().pitch(); }
    @Override public boolean isPreviewActive() { return ModelManager.getActiveModel() != null && !previewError; }
    @Override public void setPreviewRotation(float yaw, float pitch) { camera.manual(yaw, pitch); }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (!isPreviewActive() || !layout.model().contains(x, y)) return false;
        if (button == 1) { camera.angle(155, -10); return true; }
        if (button != 0) return false;
        setFocused(null);
        draggingPreview = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingPreview) {
            setPreviewRotation(getPreviewYaw() - (float) dx * .5f, getPreviewPitch() - (float) dy * .5f);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = button == 0 && draggingPreview;
        if (button == 0) draggingPreview = false;
        var focused = getFocused();
        if (focused != null && !focused.isMouseOver(x, y)) handled |= focused.mouseReleased(x, y, button);
        return super.mouseReleased(x, y, button) || handled;
    }

    @Override public void close() { client.setScreen(parent); }

    @Override
    public void removed() {
        super.removed();
        if (modelGrid != null) listScroll = modelGrid.getScrollAmount();
        if (renderer != null) renderer.close();
        renderer = null;
        modelGrid = null;
        models = null;
        draggingPreview = false;
        refreshRequested = false;
        previewError = false;
        lastPreviewFrame = 0;
        camera.reset();
        PonyStyleThumbnails.clearModels();
    }

    private static Text text(String key, Object... args) {
        return Text.translatable("text.magicaland.pony_manager." + key, args);
    }

    private static final class ModelNameScreen extends Screen {
        private final PonyManagerScreen parent;
        private final boolean create;
        private final String target;
        private String draft;
        private boolean error;
        private TextFieldWidget nameField;
        private CustomButton confirmButton;

        private ModelNameScreen(PonyManagerScreen parent, boolean create) {
            super(text(create ? "create_title" : "rename_title"));
            this.parent = parent;
            this.create = create;
            target = parent.activeName();
            draft = create ? "" : target;
        }

        @Override
        protected void init() {
            super.init();
            int formWidth = Math.min(280, width - 32);
            int x = (width - formWidth) / 2;
            int y = height / 2 - 12;
            nameField = addDrawableChild(new TextFieldWidget(textRenderer, x, y, formWidth, 20, text("name")));
            nameField.setMaxLength(32);
            nameField.setText(draft);
            nameField.setChangedListener(value -> {
                draft = value;
                error = false;
                confirmButton.active = !draft.isBlank();
            });
            int half = (formWidth - 4) / 2;
            confirmButton = addDrawableChild(new CustomButton(x, y + 28, half, 20,
                    Text.translatable("text.magicaland.config.button.confirm"), false, button -> confirm()));
            confirmButton.active = !draft.isBlank();
            addDrawableChild(new CustomButton(x + half + 4, y + 28, formWidth - half - 4, 20,
                    Text.translatable("text.magicaland.config.button.cancel"), false, button -> close()));
            setInitialFocus(nameField);
        }

        private void confirm() {
            if (draft.isBlank() || ModelManager.isEditing()) return;
            boolean success = create ? ModelManager.createModel(draft)
                    : target.equals(parent.activeName()) && ModelManager.renameActiveModel(draft);
            if (!success) { error = true; return; }
            parent.operationError = false;
            parent.revealSelection = true;
            close();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
            super.render(context, mouseX, mouseY, delta);
            if (error) {
                var lines = textRenderer.wrapLines(Text.translatable("text.magicaland.customize.preset.rename_error"),
                        Math.min(280, width - 32));
                for (int i = 0; i < lines.size(); i++)
                    context.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2,
                            height / 2 + 44 + i * textRenderer.fontHeight, 0xFFFF9999);
            }
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == 257 || key == 335) { confirm(); return true; }
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override public void tick() { nameField.tick(); }
        @Override public void close() { client.setScreen(parent); }
    }
}
