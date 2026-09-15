package top.elysianherd.magicaland.client.gui.tab.settings;

import net.minecraft.text.Text;
import net.minecraft.client.gui.tooltip.Tooltip;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;
import top.elysianherd.magicaland.client.gui.widget.Toggle;

import java.util.List;

public class GamePage implements SettingsPage {
    private static final List<String> GLOW_STYLES = List.of("current", "legacy");

    @Override
    public void build(SettingsList list, int buttonX, int buttonWidth) {
        Config config = Config.getInstance();

        Toggle replaceModelToggle = new Toggle(buttonX, 0, buttonWidth, 20,
                Text.translatable("text.magicaland.config.replace_model.name"),
                config.replacePlayerModel,
                toggle -> {
                    config.replacePlayerModel = toggle.getState();
                    Config.save();
                });
        replaceModelToggle.setTooltip(Tooltip.of(
                Text.translatable("text.magicaland.config.replace_model.tooltip")));
        list.addWidget(replaceModelToggle);

        Toggle magicGlowToggle = new Toggle(buttonX, 0, buttonWidth, 20,
                Text.translatable("text.magicaland.config.first_person_magic_glow.name"),
                config.firstPersonMagicGlow,
                toggle -> {
                    config.firstPersonMagicGlow = toggle.getState();
                    Config.save();
                });
        magicGlowToggle.setTooltip(Tooltip.of(
                Text.translatable("text.magicaland.config.first_person_magic_glow.tooltip")));
        list.addWidget(magicGlowToggle);

        int activeGlowStyle = GLOW_STYLES.indexOf(config.magicGlowStyle);
        if (activeGlowStyle < 0) activeGlowStyle = 0;
        CustomButton glowStyleButton = new CustomButton(buttonX, 0, buttonWidth,
                20, Text.translatable("text.magicaland.config.magic_glow_style.name"),
                Text.translatable("text.magicaland.config.magic_glow_style." + GLOW_STYLES.get(activeGlowStyle)).getString(),
                false, button -> {
                    int index = GLOW_STYLES.indexOf(config.magicGlowStyle);
                    if (index < 0) index = 0;
                    int next = (index + 1) % GLOW_STYLES.size();
                    config.magicGlowStyle = GLOW_STYLES.get(next);
                    ((CustomButton) button).setValue(Text.translatable(
                            "text.magicaland.config.magic_glow_style." + config.magicGlowStyle).getString());
                    ((CustomButton) button).setSegments(GLOW_STYLES.size(), next);
                    Config.save();
                });
        glowStyleButton.setSegments(GLOW_STYLES.size(), activeGlowStyle);
        list.addWidget(glowStyleButton);

        Toggle magicSoundToggle = new Toggle(buttonX, 0, buttonWidth, 20,
                Text.translatable("text.magicaland.config.magic_sounds.name"), config.magicSounds,
                toggle -> {
                    config.magicSounds = toggle.getState();
                    Config.save();
                });
        list.addWidget(magicSoundToggle);

        Toggle gazeToggle = new Toggle(buttonX, 0, buttonWidth, 20,
                Text.translatable("text.magicaland.config.automatic_gaze.name"), config.automaticGaze,
                toggle -> {
                    config.automaticGaze = toggle.getState();
                    Config.save();
                });
        gazeToggle.setTooltip(Tooltip.of(Text.translatable("text.magicaland.config.automatic_gaze.tooltip")));
        list.addWidget(gazeToggle);
    }
}
