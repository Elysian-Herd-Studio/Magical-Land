package top.elysianherd.magicaland.client.gui.tab.settings;

import top.elysianherd.magicaland.client.gui.widget.SettingsList;

@FunctionalInterface
public interface SettingsPage {
    void build(SettingsList list, int buttonX, int buttonWidth);
}
