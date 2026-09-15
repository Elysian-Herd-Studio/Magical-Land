package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.SectionLabel;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;
import top.elysianherd.magicaland.client.render.IrisPalette;
import top.elysianherd.magicaland.client.render.BodyPalette;

public class FacePage implements PonyCustomPage {
    private boolean irisExpanded;
    private boolean advancedEyesExpanded;

    @Override
    public void onEnter() {
        irisExpanded = false;
        advancedEyesExpanded = false;
    }

    @Override
    public void build(PonyCustomPageContext context, SettingsList list) {
        ModelConfig config = ModelManager.getActiveModel();
        if (config == null) return;
        ModelConfig.sanitize(config);

        int buttonWidth = context.getControlWidth();
        int buttonHeight = 20;
        int buttonX = getButtonX(context, buttonWidth);

        list.addWidget(new StyleGridWidget(buttonX, buttonWidth, config, PonyStylePart.EYE,
                () -> config.eyeStyle, style -> {
                    context.focusPart(PonyStylePart.EYE);
                    if (config.eyeStyle.equals(style)) return;
                    config.eyeStyle = style;
                    ModelManager.saveActiveModel();
                }, list), SettingsList.Alignment.RIGHT);

        list.addWidget(new SectionLabel(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.section.mane_colors.name")), SettingsList.Alignment.RIGHT);
        addIrisColors(context, list, config, buttonX, buttonWidth, buttonHeight);
        addAdvancedEyes(context, list, config, buttonX, buttonWidth, buttonHeight);
        list.addWidget(PonyCustomPageHelper.createBodyColorPicker(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.nose_color.name"), config.noseColor,
                config.noseColorLocked,
                color -> config.noseColor = color,
                locked -> config.noseColorLocked = locked), SettingsList.Alignment.RIGHT);
    }

    private void addIrisColors(PonyCustomPageContext context, SettingsList list, ModelConfig config, int x, int width, int height) {
        ColorPicker base = new PartColorPicker(context, PonyStylePart.EYE, x, 0, width, height,
                Text.translatable("text.magicaland.config.iris_color.name"), BodyPalette.hex(IrisPalette.base(config)), color -> {
                    config.irisColor = color;
                    ModelManager.requestSaveActiveModel();
                });
        base.setDisclosure(irisExpanded, expanded -> {
            irisExpanded = expanded;
            context.refreshKeepingScroll();
        }, "text.magicaland.config.iris_details");
        list.addWidget(base, SettingsList.Alignment.RIGHT);
        if (!irisExpanded) return;

        ColorPicker light = new PartColorPicker(context, PonyStylePart.EYE, x + 12, 0, width - 12, height,
                Text.translatable("text.magicaland.config.iris_light_color.name"), BodyPalette.hex(IrisPalette.light(config)), color -> {
                    config.irisLightColor = color;
                    ModelManager.requestSaveActiveModel();
                });
        light.setAutomaticColor(() -> BodyPalette.hex(IrisPalette.automaticLight(IrisPalette.base(config))));
        light.setLocked(config.irisLightColorLocked);
        light.setOnLockChanged(locked -> {
            IrisPalette.setLightLocked(config, locked);
            ModelManager.saveActiveModel();
            context.refreshKeepingScroll();
        });
        list.addWidget(light, SettingsList.Alignment.RIGHT);
        list.addWidget(new CustomButton(x + 12, 0, width - 12, height,
                Text.translatable("text.magicaland.config.iris_color.reset"), false, button -> {
                    context.focusPart(PonyStylePart.EYE);
                    IrisPalette.reset(config);
                    ModelManager.saveActiveModel();
                    context.refreshKeepingScroll();
                }, false, true), SettingsList.Alignment.RIGHT);
    }

    private void addAdvancedEyes(PonyCustomPageContext context, SettingsList list, ModelConfig config, int x, int width, int height) {
        list.addWidget(new CustomButton(x, 0, width, height,
                Text.literal(advancedEyesExpanded ? "\u25bc " : "\u25b6 ").append(Text.translatable("text.magicaland.config.advanced_eyes.name")),
                false, button -> {
                    context.focusPart(PonyStylePart.EYE);
                    advancedEyesExpanded = !advancedEyesExpanded;
                    context.refreshKeepingScroll();
                }, false, true), SettingsList.Alignment.RIGHT);
        if (!advancedEyesExpanded) return;
        addEyeColor(context, list, x, width, height, "eyelash", config.eyelashColor, color -> config.eyelashColor = color);
        addEyeColor(context, list, x, width, height, "sclera", config.scleraColor, color -> config.scleraColor = color);
        addEyeColor(context, list, x, width, height, "pupil", config.pupilColor, color -> config.pupilColor = color);
        list.addWidget(new CustomButton(x + 12, 0, width - 12, height,
                Text.translatable("text.magicaland.config.advanced_eyes.reset"), false, button -> {
                    context.focusPart(PonyStylePart.EYE);
                    config.eyelashColor = "#000000";
                    config.scleraColor = "#FFFFFF";
                    config.pupilColor = "#000000";
                    ModelManager.saveActiveModel();
                    context.refreshKeepingScroll();
                }, false, true), SettingsList.Alignment.RIGHT);
    }

    private void addEyeColor(PonyCustomPageContext context, SettingsList list, int x, int width, int height, String part, String value,
            java.util.function.Consumer<String> update) {
        ColorPicker picker = new PartColorPicker(context, PonyStylePart.EYE, x + 12, 0, width - 12, height,
                Text.translatable("text.magicaland.config." + part + "_color.name"), value, color -> {
                    update.accept(color);
                    ModelManager.requestSaveActiveModel();
                });
        list.addWidget(picker, SettingsList.Alignment.RIGHT);
    }

    private int getButtonX(PonyCustomPageContext context, int buttonWidth) {
        if (context.getWidth() < 250) {
            return context.getX() + (context.getWidth() - buttonWidth) / 2;
        }
        return context.getX() + context.getWidth() - buttonWidth - 20;
    }
}
