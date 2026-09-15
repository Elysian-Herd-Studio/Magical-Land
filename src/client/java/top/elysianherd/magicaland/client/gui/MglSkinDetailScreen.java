package top.elysianherd.magicaland.client.gui;

import net.minecraft.client.gui.DrawContext;
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
import top.elysianherd.magicaland.client.gui.ponycustom.PreviewCamera;
import top.elysianherd.magicaland.client.gui.ponycustom.PreviewGeometryBounds;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.ViewCube;
import top.elysianherd.magicaland.client.network.MglSkinClient;

public final class MglSkinDetailScreen extends Screen implements ViewCube.RotationTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(MglSkinDetailScreen.class);
    private final Screen parent;
    private final MglSkinClient.RemoteSkin skin;
    private final ModelConfig preview;
    private final PreviewCamera camera = new PreviewCamera();
    private PonyPreviewRenderer renderer;
    private String status = "";
    private boolean previewError;
    private boolean draggingPreview;
    private long lastPreviewFrame;

    public MglSkinDetailScreen(Screen parent, MglSkinClient.RemoteSkin skin) {
        super(Text.literal(skin.name()));
        this.parent = parent;
        this.skin = skin;
        this.preview = MglSkinClient.parseModel(skin);
    }

    @Override
    protected void init() {
        super.init();
        if (renderer == null) renderer = new PonyPreviewRenderer();
        int contentWidth = Math.min(520, width - 24);
        int left = (width - contentWidth) / 2;
        int infoX = left + Math.min(260, contentWidth * 5 / 11) + 12;
        int infoWidth = left + contentWidth - infoX;
        int buttonWidth = Math.max(1, (infoWidth - 4) / 2);
        addDrawableChild(new CustomButton(infoX, height - 34, buttonWidth, 20,
                text("back"), false, button -> close()));
        CustomButton importButton = addDrawableChild(new CustomButton(infoX + buttonWidth + 4, height - 34,
                infoWidth - buttonWidth - 4, 20,
                text("import"), false, button -> openImportName()));
        importButton.active = preview != null && !ModelManager.isEditing();
    }

    private void openImportName() {
        String base = skin.name().isBlank() ? "preset-" + skin.id() : skin.name();
        client.setScreen(new ImportNameScreen(this, base));
    }

    private void importModel(String name) {
        if (ModelManager.importModel(name, skin.data())) {
            status = Text.translatable("text.magicaland.mglskin.imported", name).getString();
        } else {
            status = text("import_error").getString();
        }
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int contentWidth = Math.min(520, width - 24);
        int left = (width - contentWidth) / 2;
        int previewWidth = Math.min(260, contentWidth * 5 / 11);
        Rect previewArea = new Rect(left, 34, previewWidth, Math.max(20, height - 72));
        int infoX = previewArea.right() + 12;
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(skin.name(), Math.max(20, width - infoX - 12)), infoX, 42, 0xFFFFFFFF);
        int infoY = 72;
        context.drawTextWithShadow(textRenderer, text("uploader"), infoX, infoY, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(
                skin.username().isBlank() ? "-" : skin.username(), Math.max(20, width - infoX - 12)),
                infoX, infoY + 18, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, text("preset_name"), infoX, infoY + 46, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(skin.name(),
                Math.max(20, width - infoX - 12)), infoX, infoY + 64, 0xFFFFFFFF);
        renderPreview(context, previewArea, mouseX, mouseY, delta);
        if (!status.isBlank())
            context.drawTextWithShadow(textRenderer, Text.literal(status), infoX, height - 62, 0xFFCCCCCC);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPreview(DrawContext context, Rect area, int mouseX, int mouseY, float delta) {
        if (preview == null || previewError) return;
        try {
            ModelConfig config = AppearanceAnatomy.apply(client.player == null ? null : client.player.getUuid(), preview);
            var box = PreviewGeometryBounds.framingBounds(config, null);
            long now = System.nanoTime();
            double seconds = lastPreviewFrame == 0 ? 1.0 / 60 : (now - lastPreviewFrame) / 1.0e9;
            lastPreviewFrame = now;
            var pose = camera.update(new PreviewCamera.Box(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()), area.width(), area.height(), seconds, false);
            renderer.renderPreview(context, config, area, pose, now, delta, mouseX, mouseY);
        } catch (RuntimeException failure) {
            previewError = true;
            LOGGER.warn("Unable to render shared preset preview", failure);
        }
    }

    @Override public float getPreviewYaw() { return camera.pose() == null ? 155 : camera.pose().yaw(); }
    @Override public float getPreviewPitch() { return camera.pose() == null ? -10 : camera.pose().pitch(); }
    @Override public boolean isPreviewActive() { return preview != null && !previewError; }
    @Override public void setPreviewRotation(float yaw, float pitch) { camera.manual(yaw, pitch); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int contentWidth = Math.min(520, width - 24);
        int left = (width - contentWidth) / 2;
        Rect area = new Rect(left, 34, Math.min(260, contentWidth * 5 / 11), Math.max(20, height - 72));
        if (!isPreviewActive() || !area.contains(mouseX, mouseY)) return false;
        if (button == 1) { camera.angle(155, -10); return true; }
        if (button != 0) return false;
        setFocused(null);
        draggingPreview = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingPreview) {
            setPreviewRotation(getPreviewYaw() - (float) deltaX * .5f,
                    getPreviewPitch() - (float) deltaY * .5f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void close() { client.setScreen(parent); }

    @Override
    public void removed() {
        super.removed();
        if (renderer != null) renderer.close();
        renderer = null;
        camera.reset();
        draggingPreview = false;
        lastPreviewFrame = 0;
    }
    private static Text text(String key) { return Text.translatable("text.magicaland.mglskin." + key); }

    private static final class ImportNameScreen extends Screen {
        private final MglSkinDetailScreen parent;
        private String draft;
        private TextFieldWidget field;
        private boolean error;

        private ImportNameScreen(MglSkinDetailScreen parent, String initial) {
            super(text("import_title"));
            this.parent = parent;
            this.draft = initial;
        }

        @Override
        protected void init() {
            super.init();
            int formWidth = Math.min(300, width - 32);
            int left = (width - formWidth) / 2;
            int y = height / 2 - 12;
            field = addDrawableChild(new TextFieldWidget(textRenderer, left, y, formWidth, 20,
                    text("import_name")));
            field.setMaxLength(32);
            field.setText(draft);
            field.setChangedListener(value -> {
                draft = value;
                error = false;
            });
            int half = (formWidth - 4) / 2;
            addDrawableChild(new CustomButton(left, y + 28, half, 20,
                    text("confirm"), false, button -> confirm()));
            addDrawableChild(new CustomButton(left + half + 4, y + 28, formWidth - half - 4, 20,
                    text("back"), false, button -> close()));
            setInitialFocus(field);
        }

        private void confirm() {
            if (draft.isBlank()) {
                error = true;
                return;
            }
            if (!ModelManager.getAvailableModels().stream().noneMatch(name -> name.equalsIgnoreCase(draft.trim()))) {
                error = true;
                return;
            }
            parent.importModel(draft.trim());
            client.setScreen(parent);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
            super.render(context, mouseX, mouseY, delta);
            if (error) context.drawCenteredTextWithShadow(textRenderer, text("import_name_error"),
                    width / 2, height / 2 + 44, 0xFFFF9999);
        }

        @Override public void close() { client.setScreen(parent); }
        private static Text text(String key) { return Text.translatable("text.magicaland.mglskin." + key); }
    }
}
