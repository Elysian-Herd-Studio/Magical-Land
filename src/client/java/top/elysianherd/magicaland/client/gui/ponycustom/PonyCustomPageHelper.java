package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.config.style.PonyStyleDefinition;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;

public final class PonyCustomPageHelper {
    private PonyCustomPageHelper() {}

    public static void drawWrapped(net.minecraft.client.gui.DrawContext context, Text text, int x, int y, int width, int color) {
        var font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        for (var line : font.wrapLines(text, width)) {
            context.drawTextWithShadow(font, line, x, y, color);
            y += font.fontHeight + 2;
        }
    }

    /** 显示稳定的款式编号，暂缺款式不会使后续款式重新编号。 */
    public static CustomButton createStyleButton(int x, int y, int width, int height, Text label,
            PonyStylePart part, String currentId, Consumer<String> onStyleChanged) {
        List<PonyStyleDefinition> styles = PonyStyleRegistry.stylesFor(part);
        int[] indexHolder = { Math.max(0, indexOf(styles, currentId)) };
        return new CustomButton(x, y, width, height, label,
                styleValueText(part, styles.get(indexHolder[0]).id), false,
                button -> {
                    indexHolder[0] = (indexHolder[0] + 1) % styles.size();
                    String nextId = styles.get(indexHolder[0]).id;
                    button.setValue(styleValueText(part, nextId));
                    onStyleChanged.accept(nextId);
                });
    }

    private static String styleValueText(PonyStylePart part, String id) {
        String noun = Text.translatable(part.genericNameLangKey).getString();
        return noun + id;
    }

    private static int indexOf(List<PonyStyleDefinition> styles, String id) {
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id.equals(id)) {
                return i;
            }
        }
        return 0;
    }

    public static ColorPicker createBodyColorPicker(int x, int y, int width, int height, Text label,
            String initialColor, boolean initiallyLocked,
            Consumer<String> onColorChanged, Consumer<Boolean> onLockChanged) {
        final ColorPicker[] pickerHolder = new ColorPicker[1];

        ColorPicker picker = new ColorPicker(x, y, width, height, label, initialColor, newColor -> {
            onColorChanged.accept(newColor);
            if (pickerHolder[0] != null && pickerHolder[0].isLocked()) {
                syncLockedBodyColors(newColor);
            }
            ModelManager.requestSaveActiveModel();
        });

        pickerHolder[0] = picker;
        ColorPicker.addToBodyLinkGroup(picker, initiallyLocked);
        picker.setLocked(initiallyLocked);
        picker.setOnLockChanged(locked -> {
            onLockChanged.accept(locked);
            ModelManager.saveActiveModel();
        });
        return picker;
    }

    private static void syncLockedBodyColors(String color) {
        ModelConfig config = ModelManager.getActiveModel();
        if (config == null) return;

        if (config.hornColorLocked) config.hornColor = color;
        if (config.wingColorLocked) config.wingColor = color;
        if (config.bodyColorLocked) config.bodyColor = color;
        if (config.neckColorLocked) config.neckColor = color;
        if (config.headColorLocked) config.headColor = color;
        if (config.noseColorLocked) config.noseColor = color;
        if (config.leftEarColorLocked) config.leftEarColor = color;
        if (config.rightEarColorLocked) config.rightEarColor = color;
        if (config.leftFrontLimbColorLocked) config.leftFrontLimbColor = color;
        if (config.rightFrontLimbColorLocked) config.rightFrontLimbColor = color;
        if (config.leftHindLimbColorLocked) config.leftHindLimbColor = color;
        if (config.rightHindLimbColorLocked) config.rightHindLimbColor = color;
    }
}
