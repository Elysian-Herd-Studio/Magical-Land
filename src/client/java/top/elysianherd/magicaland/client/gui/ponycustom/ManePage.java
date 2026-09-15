package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.text.Text;
import java.util.EnumSet;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.HorizontalTabBar;
import top.elysianherd.magicaland.client.gui.widget.SectionLabel;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;
import top.elysianherd.magicaland.client.gui.widget.Toggle;
import top.elysianherd.magicaland.client.render.ManeDye;
import top.elysianherd.magicaland.client.render.BodyPalette;
import top.elysianherd.magicaland.client.render.ManePalette;
import top.elysianherd.magicaland.client.render.ManeMirror;
import top.elysianherd.magicaland.client.render.ManePalette.Part;

public class ManePage implements PonyCustomPage {
    private Part selectedPart = Part.FRONT;
    private final EnumSet<Part> expandedParts = EnumSet.noneOf(Part.class);
    private final EnumSet<Part> expandedDyes = EnumSet.noneOf(Part.class);
    private final HorizontalTabBar partBar = new HorizontalTabBar(new Text[] {
            ManePartTabs.label(Part.FRONT), ManePartTabs.label(Part.BACK), ManePartTabs.label(Part.TAIL)
    }, 3, 0, 24);

    @Override
    public void onEnter() {
        selectedPart = Part.FRONT;
        expandedParts.clear();
        expandedDyes.clear();
        partBar.resetIndicator();
    }

    @Override
    public void build(PonyCustomPageContext context, SettingsList list) {
        ModelConfig config = ModelManager.getActiveModel();
        if (config == null) return;
        ModelConfig.sanitize(config);

        int buttonWidth = context.getControlWidth();
        int buttonHeight = 20;
        int buttonX = getButtonX(context, buttonWidth);

        Part part = selectedPart;
        PonyStylePart stylePart = stylePart(part);
        list.addWidget(new ManePartTabs(buttonX, buttonWidth, partBar, part, next -> {
            context.focusPart(stylePart(next));
            if (selectedPart == next) return;
            selectedPart = next;
            context.resetScroll();
            context.refreshKeepingScroll();
        }), SettingsList.Alignment.RIGHT);
        Toggle mirror = new Toggle(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.customize.styles.mirror"), ManeMirror.enabled(config, stylePart), button -> {
                    context.focusPart(stylePart);
                    ManeMirror.set(config, stylePart, button.getState());
                    ModelManager.saveActiveModel();
                });
        list.addWidget(mirror, SettingsList.Alignment.RIGHT);
        list.addWidget(new StyleGridWidget(buttonX, buttonWidth, config, stylePart,
                () -> styleId(config, part), style -> {
                    context.focusPart(stylePart);
                    if (styleId(config, part).equals(style)) return;
                    switch (part) {
                        case FRONT -> config.frontManeStyle = style;
                        case BACK -> config.backManeStyle = style;
                        case TAIL -> config.tailStyle = style;
                    }
                    ModelManager.saveActiveModel();
                    context.refreshKeepingScroll();
                }, list), SettingsList.Alignment.RIGHT);

        list.addWidget(new SectionLabel(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.section.mane_colors.name")), SettingsList.Alignment.RIGHT);
        boolean soft = !"legacy".equals(config.maneShadingMode);
        CustomButton shading = new CustomButton(buttonX, 0, buttonWidth, buttonHeight,
                Text.translatable("text.magicaland.config.mane_shading.name"),
                Text.translatable("text.magicaland.config.body_shading." + (soft ? "soft" : "legacy")).getString(), false,
                button -> {
                    context.focusPart(stylePart);
                    config.maneShadingMode = soft ? "legacy" : "soft";
                    ModelManager.saveActiveModel();
                    context.refreshKeepingScroll();
                });
        list.addWidget(shading, SettingsList.Alignment.RIGHT);
        addDye(context, list, config, part, buttonX, buttonWidth, buttonHeight);
        String label = switch (part) { case FRONT -> "front_mane_color"; case BACK -> "back_mane_color"; case TAIL -> "tail_color"; };
        addPart(context, list, config, part, label, buttonX, buttonWidth, buttonHeight, soft);
    }

    private void addDye(PonyCustomPageContext context, SettingsList list, ModelConfig config, Part part,
            int x, int width, int height) {
        boolean supported = ManeDye.supports(config, part);
        Toggle toggle = new Toggle(x, 0, width, height,
                Text.translatable("text.magicaland.config.mane_dye.name"), config.maneDyeEnabled, button -> {
                    context.focusPart(stylePart(part));
                    config.maneDyeEnabled = button.getState();
                    ModelManager.saveActiveModel();
                    context.refreshKeepingScroll();
                });
        toggle.active = supported;
        list.addWidget(toggle, SettingsList.Alignment.RIGHT);
    }

    private void addDyePart(PonyCustomPageContext context, SettingsList list, ModelConfig config,
            Part part, int x, int width, int height) {
        if (!ManeDye.enabled(config, part)) return;
        boolean expanded = expandedDyes.contains(part);
        String partKey = switch (part) { case FRONT -> "front"; case BACK -> "back"; case TAIL -> "tail"; };
        list.addWidget(new CustomButton(x, 0, width, height,
                Text.literal(expanded ? "\u25bc " : "\u25b6 ").append(Text.translatable("text.magicaland.config.mane_dye.regions." + partKey)),
                false, button -> {
                    context.focusPart(stylePart(part));
                    if (expanded) expandedDyes.remove(part); else expandedDyes.add(part);
                    context.refreshKeepingScroll();
                }, false, true), SettingsList.Alignment.RIGHT);
        if (!expanded) return;
        for (int i = 0; i < ManeDye.REGION_COUNT; i++) {
            final int region = i;
            ColorPicker picker = new PartColorPicker(context, stylePart(part), x + 12, 0, width - 12, height,
                    Text.translatable("text.magicaland.config.mane_dye.region", region + 1),
                    BodyPalette.hex(ManeDye.colors(config, part, region).base()), color -> {
                        ManeDye.setColor(config, part, region, color);
                        ModelManager.requestSaveActiveModel();
                    });
            picker.setAutomaticColor(() -> BodyPalette.hex(ManePalette.base(config, part)));
            picker.setLocked(ManeDye.linked(config, part, region));
            picker.setOnLockChanged(locked -> {
                ManeDye.setLinked(config, part, region, locked);
                ModelManager.saveActiveModel();
                context.refreshKeepingScroll();
            });
            list.addWidget(picker, SettingsList.Alignment.RIGHT);
        }
        list.addWidget(new CustomButton(x + 12, 0, width - 12, height,
                Text.translatable("text.magicaland.config.mane_dye.regions.reset"), false, button -> {
                    context.focusPart(stylePart(part));
                    ManeDye.resetRegions(config, part);
                    ModelManager.saveActiveModel();
                    context.refreshKeepingScroll();
                }, false, true), SettingsList.Alignment.RIGHT);
    }

    private void addPart(PonyCustomPageContext context, SettingsList list, ModelConfig config,
            Part part, String label, int x, int width, int height, boolean soft) {
        ColorPicker base = new PartColorPicker(context, stylePart(part), x, 0, width, height,
                Text.translatable("text.magicaland.config." + label + ".name"),
                BodyPalette.hex(ManePalette.base(config, part)), color -> {
                    ManePalette.setBase(config, part, color);
                    ModelManager.requestSaveActiveModel();
                });
        if (part != Part.FRONT) {
            base.setAutomaticColor(() -> BodyPalette.hex(ManePalette.base(config, Part.FRONT)));
            base.setLocked(ManePalette.linked(config, part));
            base.setOnLockChanged(locked -> {
                ManePalette.setLinked(config, part, locked);
                ModelManager.saveActiveModel();
                context.refreshKeepingScroll();
            });
        }
        boolean hasDetails = soft && !ManePalette.linked(config, part);
        if (hasDetails) {
            boolean expanded = expandedParts.contains(part);
            base.setDisclosure(expanded, value -> {
                if (value) expandedParts.add(part);
                else expandedParts.remove(part);
                context.refreshKeepingScroll();
            });
        }
        list.addWidget(base, SettingsList.Alignment.RIGHT);
        if (hasDetails && expandedParts.contains(part)) {
            addStop(context, list, config, part, false, x + 12, width - 12, height);
            addStop(context, list, config, part, true, x + 12, width - 12, height);
        }
        addDyePart(context, list, config, part, x + 12, width - 12, height);
    }

    private void addStop(PonyCustomPageContext context, SettingsList list, ModelConfig config,
            Part part, boolean highlight, int x, int width, int height) {
        String key = highlight ? "body_highlight" : "body_shadow";
        ColorPicker picker = new PartColorPicker(context, stylePart(part), x, 0, width, height,
                Text.translatable("text.magicaland.config." + key + ".name"),
                BodyPalette.hex(ManePalette.stop(config, part, highlight)), color -> {
                    ManePalette.setStop(config, part, highlight, color);
                    ModelManager.requestSaveActiveModel();
                });
        picker.setAutomaticColor(() -> BodyPalette.hex(ManePalette.automatic(ManePalette.base(config, part), highlight)));
        picker.setLocked(ManePalette.stopLocked(config, part, highlight));
        picker.setOnLockChanged(locked -> {
            ManePalette.setStopLocked(config, part, highlight, locked);
            ModelManager.saveActiveModel();
            context.refreshKeepingScroll();
        });
        list.addWidget(picker, SettingsList.Alignment.RIGHT);
    }

    public PonyStylePart selectedStylePart() { return stylePart(selectedPart); }

    private static PonyStylePart stylePart(Part part) {
        return switch (part) { case FRONT -> PonyStylePart.FRONT_MANE; case BACK -> PonyStylePart.BACK_MANE; case TAIL -> PonyStylePart.TAIL; };
    }

    private static String styleId(ModelConfig config, Part part) {
        return switch (part) { case FRONT -> config.frontManeStyle; case BACK -> config.backManeStyle; case TAIL -> config.tailStyle; };
    }

    private int getButtonX(PonyCustomPageContext context, int buttonWidth) {
        if (context.getWidth() < 250) {
            return context.getX() + (context.getWidth() - buttonWidth) / 2;
        }
        return context.getX() + context.getWidth() - buttonWidth - 20;
    }
}
