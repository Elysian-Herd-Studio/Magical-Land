package top.csituka.magicaland.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import top.csituka.magicaland.client.config.ModelConfig;
import top.csituka.magicaland.client.config.ModelManager;
import top.csituka.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.csituka.magicaland.client.gui.ponymanager.ModelGridWidget;
import top.csituka.magicaland.client.gui.widget.CustomButton;
import top.csituka.magicaland.client.network.MglSkinClient;

import java.util.Comparator;
import java.util.List;

public final class MglSkinUploadScreen extends Screen {
    private final MglCloudPresetScreen parent;
    private String selectedName = "";
    private ModelGridWidget modelGrid;
    private CustomButton uploadButton;
    private boolean uploading;

    public MglSkinUploadScreen(MglCloudPresetScreen parent) {
        super(text("upload_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(460, width - 24);
        int left = (width - contentWidth) / 2;
        List<ModelGridWidget.ModelEntry> entries = ModelManager.getAvailableModels().stream()
                .sorted(Comparator.naturalOrder())
                .map(name -> new ModelGridWidget.ModelEntry(name, ModelManager.getModelPreview(name)))
                .toList();
        modelGrid = addDrawableChild(new ModelGridWidget(
                new Rect(left, 70, contentWidth, Math.max(20, height - 112)), entries,
                () -> selectedName, this::selectModel));
        modelGrid.active = !uploading;
        int half = (contentWidth - 4) / 2;
        addDrawableChild(new CustomButton(left, height - 34, half, 20,
                text("back"), false, button -> close()));
        uploadButton = addDrawableChild(new CustomButton(left + half + 4, height - 34,
                contentWidth - half - 4, 20, text("upload"), false, button -> upload()));
        uploadButton.active = !uploading && !selectedName.isBlank();
    }

    private void selectModel(String name) {
        if (uploading) return;
        selectedName = name;
        uploadButton.active = true;
    }

    private void upload() {
        if (uploading || selectedName.isBlank()) return;
        ModelConfig model = ModelManager.getModelPreview(selectedName);
        if (model == null) {
            parent.uploadFailed(text("upload_read_error").getString());
            close();
            return;
        }
        uploading = true;
        modelGrid.active = false;
        uploadButton.active = false;
        MglSkinClient.upload(model, name -> {
            parent.uploadComplete(name);
            if (client.currentScreen == this) client.setScreen(parent);
        }, error -> {
            parent.uploadFailed(error);
            if (client.currentScreen == this) client.setScreen(parent);
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer,
                uploading ? text("uploading") : title, width / 2, 20, 0xFFFFFFFF);
        int contentWidth = Math.min(460, width - 24);
        var lines = textRenderer.wrapLines(text("upload_private_hint"), contentWidth);
        for (int i = 0; i < Math.min(3, lines.size()); i++) context.drawCenteredTextWithShadow(textRenderer,
                lines.get(i), width / 2, 36 + i * textRenderer.fontHeight, 0xFFAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { if (!uploading) client.setScreen(parent); }
    private static Text text(String key) { return Text.translatable("text.magicaland.mglskin." + key); }
}
