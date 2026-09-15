package top.elysianherd.magicaland.client.gui.tab.settings;

import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

import java.util.List;

public class GeneralPage implements SettingsPage {
    private static final List<String> BUTTON_MODES = List.of("all", "button_only", "hidden");

    @Override
    public void build(SettingsList list, int buttonX, int buttonWidth) {
        Config config = Config.getInstance();

        int activeIdx = BUTTON_MODES.indexOf(config.mainMenuPonyButton);
        if (activeIdx < 0) activeIdx = 0;

        String modeKey = "text.magicaland.config.main_menu_pony_button." + BUTTON_MODES.get(activeIdx);
        CustomButton btn = new CustomButton(buttonX, 0, buttonWidth, 20,
                Text.translatable("text.magicaland.config.main_menu_pony_button.name"),
                Text.translatable(modeKey).getString(),
                false, b -> {
                    int idx = BUTTON_MODES.indexOf(config.mainMenuPonyButton);
                    if (idx < 0) idx = 0;
                    String next = BUTTON_MODES.get((idx + 1) % BUTTON_MODES.size());
                    String nextKey = "text.magicaland.config.main_menu_pony_button." + next;
                    ((CustomButton) b).setValue(Text.translatable(nextKey).getString());
                    ((CustomButton) b).setSegments(BUTTON_MODES.size(),
                            (idx + 1) % BUTTON_MODES.size());
                    config.mainMenuPonyButton = next;
                    Config.save();
                });
        btn.setTooltip(Tooltip.of(Text.translatable("text.magicaland.config.main_menu_pony_button.tooltip")));
        btn.setSegments(BUTTON_MODES.size(), activeIdx);
        list.addWidget(btn);
    }
}
