package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.SectionLabel;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

public class ModelPage implements PonyCustomPage {
    private boolean createOpen;
    private boolean renameOpen;
    private String renameTarget;
    private String deleteTarget;
    private String draft = "";
    private boolean error;
    private boolean returnAfterAction;
    private PonyCustomPageContext context;

    @Override
    public void onEnter() {
        createOpen = false;
        renameOpen = false;
        deleteTarget = null;
        draft = "";
        error = false;
        returnAfterAction = false;
    }

    public boolean beginCreate(boolean returnToEditor) {
        if (!ModelManager.isEditing() || isEditingPreset()) return false;
        onEnter();
        createOpen = true;
        returnAfterAction = returnToEditor;
        return true;
    }

    public boolean beginDelete(boolean returnToEditor) {
        ModelConfig current = ModelManager.getActiveModel();
        if (!ModelManager.isEditing() || isEditingPreset() || current == null || ModelManager.getAvailableModels().size() <= 1) return false;
        onEnter();
        deleteTarget = current.name;
        returnAfterAction = returnToEditor;
        return true;
    }

    @Override
    public void build(PonyCustomPageContext context, SettingsList list) {
        this.context = context;
        int x = context.getX() + 10;
        int width = context.getControlWidth();
        if (createOpen || renameOpen || deleteTarget != null) {
            buildConfirmation(x, width);
            return;
        }
        ModelManager.refreshModelList();
        List<String> models = ModelManager.getAvailableModels();
        list.addWidget(new CustomButton(x, 0, width, 20, tr("preset.back_to_edit"), false,
                button -> context.openPage(PonyCustomPageContext.Page.MAIN, -1)), SettingsList.Alignment.RIGHT);
        list.addWidget(new SectionLabel(x, 0, width, 20, tr("preset.saved")), SettingsList.Alignment.RIGHT);
        ModelConfig current = ModelManager.getActiveModel();
        for (String name : models) {
            boolean selected = current != null && name.equals(current.name);
            list.addWidget(new CustomButton(x, 0, width, 20, Text.literal((selected ? "✓ " : "") + name + (ModelManager.isPresetDirty(name) ? " *" : "")),
                    selected, button -> {
                        if (selected) return;
                        if (ModelManager.loadModel(name)) context.reinit();
                        else { error = true; context.reinit(); }
                    }), SettingsList.Alignment.RIGHT);
        }
        list.addWidget(new CustomButton(x, 0, width, 20, tr("preset.create"), false, button -> {
            if (beginCreate(false)) context.reinit();
        }), SettingsList.Alignment.RIGHT);
        CustomButton duplicate = new CustomButton(x, 0, width, 20, tr("preset.duplicate"), false, button -> {
            if (ModelManager.duplicateActiveModel(tr("preset.copy_suffix").getString())) {
                error = false;
                context.reinit();
            } else {
                error = true;
                context.reinit();
            }
        });
        duplicate.active = current != null;
        list.addWidget(duplicate, SettingsList.Alignment.RIGHT);
        CustomButton rename = new CustomButton(x, 0, width, 20, tr("preset.rename"), false, button -> {
            renameOpen = true;
            renameTarget = ModelManager.getActiveModel().name;
            draft = renameTarget;
            error = false;
            context.reinit();
        });
        rename.active = current != null;
        list.addWidget(rename, SettingsList.Alignment.RIGHT);
        CustomButton delete = new CustomButton(x, 0, width, 20, tr("preset.delete"), false, button -> {
            if (beginDelete(false)) context.reinit();
        });
        delete.active = current != null && models.size() > 1;
        list.addWidget(delete, SettingsList.Alignment.RIGHT);
    }

    private void buildConfirmation(int x, int width) {
        Text heading = confirmationHeading();
        int headingHeight = MinecraftClient.getInstance().textRenderer.wrapLines(heading, width).size() * 11;
        int y = context.getY() + 18 + headingHeight;
        if (createOpen || renameOpen) {
            TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, width,
                    20, Text.translatable("text.magicaland.config.create_model.name_placeholder"));
            field.setMaxLength(32);
            field.setText(draft);
            field.setChangedListener(value -> { draft = value; error = false; });
            context.getScreen().addConsoleWidget(field);
            context.getScreen().setFocused(field);
            field.setFocused(true);
        }
        int half = (width - 4) / 2;
        int buttonsY = createOpen || renameOpen ? y + 30 : y + 4;
        context.getScreen().addConsoleWidget(new CustomButton(x, buttonsY, half, 20,
                Text.translatable("text.magicaland.config.button.confirm"), false, button -> confirm()));
        context.getScreen().addConsoleWidget(new CustomButton(x + half + 4, buttonsY, half, 20,
                Text.translatable("text.magicaland.config.button.cancel"), false, button -> cancel()));
    }

    private void confirm() {
        boolean success = createOpen ? ModelManager.createModel(draft) : renameOpen
                ? ModelManager.getActiveModel() != null && ModelManager.getActiveModel().name.equals(renameTarget) && ModelManager.renameActiveModel(draft)
                : ModelManager.getActiveModel() != null && ModelManager.getActiveModel().name.equals(deleteTarget)
                        && ModelManager.deleteModel(deleteTarget);
        if (success) finishAction();
        else { error = true; context.reinit(); }
    }

    private void finishAction() {
        boolean back = returnAfterAction;
        onEnter();
        if (back) context.openPage(PonyCustomPageContext.Page.MAIN, -1);
        else context.reinit();
    }
    private void cancel() { finishAction(); }

    private Text confirmationHeading() {
        return deleteTarget == null ? tr(renameOpen ? "preset.rename" : "preset.create")
                : Text.translatable("text.magicaland.customize.preset.delete_confirm", deleteTarget);
    }

    @Override
    public boolean keyPressed(int key) {
        if (!createOpen && !renameOpen && deleteTarget == null) return false;
        if (key == 256) { cancel(); return true; }
        if (key == 257 || key == 335) { confirm(); return true; }
        return false;
    }

    @Override
    public boolean isEditingPreset() { return createOpen || renameOpen || deleteTarget != null; }

    @Override
    public void render(PonyCustomPageContext context, DrawContext draw, int mouseX, int mouseY, float delta, float alpha) {
        int x = context.getX() + 10;
        int y = context.getY() + 10;
        if (isEditingPreset()) PonyCustomPageHelper.drawWrapped(draw, confirmationHeading(), x, y,
                context.getControlWidth(), deleteTarget == null ? 0xFFFFFFFF : 0xFFFFB7B7);
        if (error) PonyCustomPageHelper.drawWrapped(draw, tr("preset.operation_error"), x, context.getY() + 102,
                context.getControlWidth(), 0xFFFF9999);
    }

    private static Text tr(String key) { return Text.translatable("text.magicaland.customize." + key); }
}
