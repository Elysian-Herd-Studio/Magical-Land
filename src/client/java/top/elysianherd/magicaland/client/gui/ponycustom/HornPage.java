package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.widget.SectionLabel;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;
import top.elysianherd.magicaland.client.gui.widget.Toggle;

public class HornPage implements PonyCustomPage {
    @Override
    public void build(PonyCustomPageContext context, SettingsList list) {
        ModelConfig config = ModelManager.getActiveModel();
        if (config == null) return;

        int buttonWidth = context.getControlWidth();
        int buttonHeight = 20;
        int buttonX = getButtonX(context, buttonWidth);

        list.addWidget(new SectionLabel(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.section.mane_styles.name")), SettingsList.Alignment.RIGHT);

        list.addWidget(new Toggle(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.show_wings.name"), config.showWings,
                toggle -> {
                    config.showWings = toggle.getState();
                    ModelManager.saveActiveModel();
                }), SettingsList.Alignment.RIGHT);

        list.addWidget(new Toggle(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.show_horn.name"), config.showHorn,
                toggle -> {
                    config.showHorn = toggle.getState();
                    ModelManager.saveActiveModel();
                }), SettingsList.Alignment.RIGHT);

        list.addWidget(new SectionLabel(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.section.mane_colors.name")), SettingsList.Alignment.RIGHT);
        list.addWidget(PonyCustomPageHelper.createBodyColorPicker(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.horn_color.name"), config.hornColor,
                config.hornColorLocked,
                color -> config.hornColor = color,
                locked -> config.hornColorLocked = locked), SettingsList.Alignment.RIGHT);
        list.addWidget(PonyCustomPageHelper.createBodyColorPicker(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.wing_color.name"), config.wingColor,
                config.wingColorLocked,
                color -> config.wingColor = color,
                locked -> config.wingColorLocked = locked), SettingsList.Alignment.RIGHT);
    }

    private int getButtonX(PonyCustomPageContext context, int buttonWidth) {
        if (context.getWidth() < 250) {
            return context.getX() + (context.getWidth() - buttonWidth) / 2;
        }
        return context.getX() + context.getWidth() - buttonWidth - 20;
    }
}
