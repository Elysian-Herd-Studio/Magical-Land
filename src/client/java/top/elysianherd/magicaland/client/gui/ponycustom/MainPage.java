package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

public class MainPage implements PonyCustomPage {
    @Override
    public void build(PonyCustomPageContext context, SettingsList list) {
        int buttonWidth = Math.min(180, context.getWidth() / 2);
        int buttonHeight = 20;
        int buttonX = getButtonX(context, buttonWidth);

        CustomButton quitButton = new CustomButton(buttonX, 0, buttonWidth, buttonHeight,
                Text.literal("\u2190 " + Text.translatable("text.magicaland.config.button.quit_edit").getString()),
                false,
                button -> {
                    ModelManager.saveActiveModel();
                    context.openPage(PonyCustomPageContext.Page.MODEL, 0);
                }, false, true);
        list.addWidget(quitButton, SettingsList.Alignment.RIGHT);

        addPageButton(list, context, buttonX, buttonWidth, buttonHeight,
                "text.magicaland.config.mane_menu.name", PonyCustomPageContext.Page.MANE);
        addPageButton(list, context, buttonX, buttonWidth, buttonHeight,
                "text.magicaland.config.face_menu.name", PonyCustomPageContext.Page.FACE);
        addPageButton(list, context, buttonX, buttonWidth, buttonHeight,
                "text.magicaland.config.horn_menu.name", PonyCustomPageContext.Page.HORN);
        addPageButton(list, context, buttonX, buttonWidth, buttonHeight,
                "text.magicaland.config.body_menu.name", PonyCustomPageContext.Page.BODY);
        addPageButton(list, context, buttonX, buttonWidth, buttonHeight,
                "text.magicaland.config.glow_menu.name", PonyCustomPageContext.Page.GLOW);
    }

    private void addPageButton(SettingsList list, PonyCustomPageContext context, int x, int width, int height,
            String labelKey, PonyCustomPageContext.Page page) {
        list.addWidget(new CustomButton(x, 0, width, height, Text.translatable(labelKey), false,
                button -> context.openPage(page, 1), true), SettingsList.Alignment.RIGHT);
    }

    private int getButtonX(PonyCustomPageContext context, int buttonWidth) {
        if (context.getWidth() < 250) {
            return context.getX() + (context.getWidth() - buttonWidth) / 2;
        }
        return context.getX() + context.getWidth() - buttonWidth - 20;
    }
}
