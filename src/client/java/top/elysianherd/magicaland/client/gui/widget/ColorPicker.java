package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ColorPicker extends ClickableWidget {
    public static ColorPicker openPicker = null;

    private static final List<ColorPicker> bodyLinkGroup = new ArrayList<>();

    private final Consumer<String> onColorChanged;
    private Consumer<Boolean> onLockChanged;
    private Consumer<Boolean> onExpandedChanged;
    private boolean expanded;
    private String disclosureNarrationKey = "text.magicaland.config.color_details";
    private Supplier<String> automaticColor;
    private String currentColor;
    public boolean open = false;
    private float currentAlpha = 0.15f;

    private static final int PICKER_WIDTH = 75;
    private static final int PICKER_HEIGHT = 103;
    private static final int HUE_HEIGHT = 8;
    private static final int PADDING = 4;
    private static final int INPUT_HEIGHT = 12;
    private static final int LOCK_SIZE = 12;
    private static final int DISCLOSURE_WIDTH = 18;

    private float h, s, v;
    private String hexInput = "";
    private boolean inputFocused = false;
    private int cursorTick = 0;

    private boolean locked = false;
    private boolean syncing = false;

    public static void addToBodyLinkGroup(ColorPicker picker, boolean initiallyLocked) {
        picker.locked = initiallyLocked;
        
        bodyLinkGroup.add(picker);
        
        if (initiallyLocked && bodyLinkGroup.size() > 1) {
            for (ColorPicker other : bodyLinkGroup) {
                if (other != picker && other.locked) {
                    picker.syncing = true;
                    picker.setColorSilent(other.currentColor);
                    if (picker.onColorChanged != null) {
                        picker.onColorChanged.accept(other.currentColor);
                    }
                    picker.syncing = false;
                    break;
                }
            }
        }
    }

    public static void clearBodyLinkGroup() {
        bodyLinkGroup.clear();
    }

    private static void syncFrom(ColorPicker source) {
        if (!source.locked) return;
        for (ColorPicker picker : bodyLinkGroup) {
            if (picker != source && picker.locked && !picker.syncing) {
                picker.syncing = true;
                picker.setColorSilent(source.currentColor);
                if (picker.onColorChanged != null) {
                    picker.onColorChanged.accept(source.currentColor);
                }
                picker.syncing = false;
            }
        }
    }
    
    private enum DragMode {
        NONE,
        SB_SQUARE,
        HUE_SLIDER
    }
    
    private DragMode dragMode = DragMode.NONE;

    public ColorPicker(int x, int y, int width, int height, Text message, String initialColor, Consumer<String> onColorChanged) {
        super(x, y, width, height, message);
        this.currentColor = initialColor;
        this.onColorChanged = onColorChanged;
        parseColor(initialColor);
        this.hexInput = getHexNoAlpha();
    }

    public void setColorSilent(String color) {
        this.currentColor = color;
        parseColor(color);
        this.hexInput = getHexNoAlpha();
    }

    public void setOnLockChanged(Consumer<Boolean> onLockChanged) {
        this.onLockChanged = onLockChanged;
    }

    public void setDisclosure(boolean expanded, Consumer<Boolean> onExpandedChanged) {
        setDisclosure(expanded, onExpandedChanged, "text.magicaland.config.color_details");
    }

    public void setDisclosure(boolean expanded, Consumer<Boolean> onExpandedChanged, String narrationKey) {
        this.expanded = expanded;
        this.onExpandedChanged = onExpandedChanged;
        this.disclosureNarrationKey = narrationKey;
    }

    private void changeExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        open = false;
        inputFocused = false;
        dragMode = DragMode.NONE;
        if (openPicker == this) openPicker = null;
        this.playDownSound(MinecraftClient.getInstance().getSoundManager());
        onExpandedChanged.accept(expanded);
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        if (locked && automaticColor != null) {
            open = false;
            inputFocused = false;
            dragMode = DragMode.NONE;
            if (openPicker == this) openPicker = null;
            refreshAutomaticColor();
        }
    }

    public void setAutomaticColor(Supplier<String> automaticColor) {
        this.automaticColor = automaticColor;
        setLocked(locked);
    }

    private void refreshAutomaticColor() {
        if (automaticColor == null || !locked) return;
        String color = automaticColor.get();
        if (color != null && !color.equals(currentColor)) setColorSilent(color);
    }

    public boolean isLocked() {
        return this.locked;
    }

    private void parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() > 6) hex = hex.substring(hex.length() - 6);
            int colorVal = Integer.parseInt(hex, 16);
            int r = (colorVal >> 16) & 0xFF;
            int g = (colorVal >> 8) & 0xFF;
            int b = colorVal & 0xFF;

            float[] hsv = Color.RGBtoHSB(r, g, b, null);
            this.h = hsv[0];
            this.s = hsv[1];
            this.v = hsv[2];
        } catch (Exception e) {
            this.h = 0;
            this.s = 0;
            this.v = 1;
        }
    }

    private String getHexColor() {
        int rgb = Color.HSBtoRGB(h, s, v);
        return String.format("#FF%06X", (rgb & 0xFFFFFF));
    }

    private String getHexNoAlpha() {
        int rgb = Color.HSBtoRGB(h, s, v);
        return String.format("#%06X", (rgb & 0xFFFFFF));
    }

    private int getPickerX() { return this.getX() + this.width - PICKER_WIDTH; }
    private int getPickerY() {
        int belowY = this.getY() + this.height + 8;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (belowY + PICKER_HEIGHT > screenHeight) {
            return this.getY() - 8 - PICKER_HEIGHT;
        }
        return belowY;
    }
    private int getSBSize() { return PICKER_WIDTH - PADDING * 2; }
    private int getSBX() { return getPickerX() + PADDING; }
    private int getSBY() { return getPickerY() + PADDING; }
    private int getHueY() { return getSBY() + getSBSize() + PADDING; }
    private int getInputY() { return getHueY() + HUE_HEIGHT + PADDING; }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible) return false;
        boolean overButton = mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height;
        if (open) {
            int px = getPickerX();
            int py = getPickerY();
            boolean overPicker = mouseX >= px && mouseX < px + PICKER_WIDTH && mouseY >= py && mouseY < py + PICKER_HEIGHT;
            return overButton || overPicker;
        }
        return overButton;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active || button != 0) return false;

        this.dragMode = DragMode.NONE;
        this.inputFocused = false;

        if (onExpandedChanged != null && mouseX >= getX() && mouseX < getX() + DISCLOSURE_WIDTH
                && mouseY >= getY() && mouseY < getY() + height) {
            changeExpanded(!expanded);
            return true;
        }

        if ((bodyLinkGroup.contains(this) || automaticColor != null) && mouseX >= this.getX() + this.width - 50 && mouseX < this.getX() + this.width - 30 && mouseY >= this.getY() + (this.height - 8) / 2 && mouseY < this.getY() + (this.height - 8) / 2 + 10) {
            setLocked(!this.locked);
            if (this.onLockChanged != null) {
                this.onLockChanged.accept(this.locked);
            }
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }

        if (automaticColor != null && locked) return isMouseOver(mouseX, mouseY);

        if (mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height) {
            if (!this.open) {
                if (openPicker != null && openPicker != this) {
                    openPicker.open = false;
                }
                openPicker = this;
            } else {
                openPicker = null;
            }
            this.open = !this.open;
            if (open) {
                this.hexInput = getHexNoAlpha();
            }
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }

        if (open) {
            int sbX = getSBX();
            int sbY = getSBY();
            int sbSize = getSBSize();
            int hueY = getHueY();
            int inputY = getInputY();

            if (mouseX >= sbX && mouseX <= sbX + sbSize && mouseY >= sbY && mouseY <= sbY + sbSize) {
                this.dragMode = DragMode.SB_SQUARE;
                updateFromMouse(mouseX, mouseY);
                return true;
            } 
            
            if (mouseX >= sbX && mouseX <= sbX + sbSize && mouseY >= hueY - 4 && mouseY <= hueY + HUE_HEIGHT + 4) {
                this.dragMode = DragMode.HUE_SLIDER;
                updateFromMouse(mouseX, mouseY);
                return true;
            }

            if (mouseX >= sbX && mouseX <= sbX + sbSize && mouseY >= inputY && mouseY <= inputY + INPUT_HEIGHT) {
                this.inputFocused = true;
                this.setFocused(true);
                return true;
            }

            int px = getPickerX();
            int py = getPickerY();
            if (mouseX >= px && mouseX < px + PICKER_WIDTH && mouseY >= py && mouseY < py + PICKER_HEIGHT) {
                return true;
            }
            
            this.open = false;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (open && inputFocused) {
            String validChars = "0123456789ABCDEFabcdef#";
            if (validChars.indexOf(chr) != -1) {
                if (hexInput.length() < 7 || (chr == '#' && !hexInput.contains("#"))) {
                    hexInput += chr;
                    tryParseHexInput();
                }
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (visible && active && isFocused() && !open && onExpandedChanged != null) {
            if (keyCode == 262 || keyCode == 263) {
                changeExpanded(keyCode == 262);
                return true;
            }
            if (keyCode == 257 || keyCode == 335 || keyCode == 32) {
                changeExpanded(!expanded);
                return true;
            }
        }
        if (open && inputFocused) {
            if (keyCode == 259) {
                if (!hexInput.isEmpty()) {
                    hexInput = hexInput.substring(0, hexInput.length() - 1);
                    tryParseHexInput();
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                inputFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void tryParseHexInput() {
        String hex = hexInput;
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) {
            try {
                int colorVal = Integer.parseInt(hex, 16);
                int r = (colorVal >> 16) & 0xFF;
                int g = (colorVal >> 8) & 0xFF;
                int b = colorVal & 0xFF;
                float[] hsv = Color.RGBtoHSB(r, g, b, null);
                this.h = hsv[0];
                this.s = hsv[1];
                this.v = hsv[2];
                updateColor();
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (open && button == 0 && dragMode != DragMode.NONE) {
            updateFromMouse(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragMode = DragMode.NONE;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateFromMouse(double mouseX, double mouseY) {
        int sbX = getSBX();
        int sbY = getSBY();
        int sbSize = getSBSize();

        if (dragMode == DragMode.SB_SQUARE) {
            this.s = MathHelper.clamp((float) (mouseX - sbX) / sbSize, 0, 1);
            this.v = MathHelper.clamp(1.0f - (float) (mouseY - sbY) / sbSize, 0, 1);
            updateColor();
        } else if (dragMode == DragMode.HUE_SLIDER) {
            this.h = MathHelper.clamp((float) (mouseX - sbX) / sbSize, 0, 1);
            updateColor();
        }
    }

    private void updateColor() {
        this.currentColor = getHexColor();
        if (!inputFocused) {
            this.hexInput = getHexNoAlpha();
        }
        if (onColorChanged != null) {
            onColorChanged.accept(this.currentColor);
        }
        if (!syncing) {
            syncFrom(this);
        }
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.alpha <= 0.0f) return;
        refreshAutomaticColor();
        renderMainButton(context, mouseX, mouseY);
        if (open && !externalOverlay) {
            renderPicker(context, mouseX, mouseY);
        }
    }

    private void renderMainButton(DrawContext context, int mouseX, int mouseY) {
        boolean hovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
        float targetAlpha = (hovered || open) ? 0.35f : 0.15f;

        if (currentAlpha < targetAlpha) {
            currentAlpha = Math.min(targetAlpha, currentAlpha + 0.05f);
        } else if (currentAlpha > targetAlpha) {
            currentAlpha = Math.max(targetAlpha, currentAlpha - 0.05f);
        }

        int alphaVal = (int) (currentAlpha * this.alpha * 255);
        int textAlpha = (int) (this.alpha * 255);

        if (alphaVal > 0) {
            fillRoundedRect(context, this.getX(), this.getY(), this.width, this.height, (alphaVal << 24) | 0xFFFFFF);
        }

        if (textAlpha > 0) {
            int labelX = getX() + (onExpandedChanged == null ? 6 : DISCLOSURE_WIDTH);
            int labelRight = getX() + width - (bodyLinkGroup.contains(this) || automaticColor != null ? 54 : 38);
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(getMessage().getString(), Math.max(0, labelRight - labelX)),
                    labelX, getY() + (height - 8) / 2, (textAlpha << 24) | 0xFFFFFF);
            if (onExpandedChanged != null) {
                drawDisclosure(context, mouseX, mouseY, (textAlpha << 24) | 0xFFFFFF);
            }
        }

        drawLockIcon(context, this.alpha);

        int previewWidth = 30;
        int previewHeight = 14;
        int previewX = this.getX() + this.width - previewWidth - 4;
        int previewY = this.getY() + (this.height - previewHeight) / 2;

        int combinedAlpha = MathHelper.clamp((int) (this.alpha * 255), 0, 255);
        if (combinedAlpha > 0) {
            try {
                String hex = currentColor.startsWith("#") ? currentColor.substring(1) : currentColor;
                if (hex.length() > 6) hex = hex.substring(hex.length() - 6);
                int colorInt = (int) Long.parseLong(hex, 16);
                fillRoundedRect(context, previewX, previewY, previewWidth, previewHeight, (combinedAlpha << 24) | (colorInt & 0xFFFFFF));
            } catch (Exception e) {
                fillRoundedRect(context, previewX, previewY, previewWidth, previewHeight, (combinedAlpha << 24) | 0xFFFFFF);
            }
        }
    }

    private void drawDisclosure(DrawContext context, int mouseX, int mouseY, int color) {
        int x = getX() + 6;
        int y = getY() + height / 2;
        if (mouseX >= getX() && mouseX < getX() + DISCLOSURE_WIDTH
                && mouseY >= getY() && mouseY < getY() + height) {
            context.fill(getX() + 2, getY() + 2, getX() + DISCLOSURE_WIDTH - 2, getY() + height - 2,
                    ((int) (alpha * 40) << 24) | 0xFFFFFF);
        }
        for (int i = 0; i < 4; i++) {
            if (expanded) context.fill(x + i, y - 2 + i, x + 7 - i, y - 1 + i, color);
            else context.fill(x + i, y - 3 + i, x + i + 1, y + 4 - i, color);
        }
    }

    private void drawLockIcon(DrawContext context, float alpha) {
        if (!bodyLinkGroup.contains(this) && automaticColor == null) return;

        int lx = this.getX() + this.width - 50;
        int ly = this.getY() + (this.height - 8) / 2;
        String lockChar = locked ? "\uD83D\uDD12" : "\uD83D\uDD13";

        int baseColor = locked ? 0xCC8844 : 0xFFFFFF;
        int baseAlpha = locked ? 255 : 136;
        int combinedAlpha = MathHelper.clamp((int) (baseAlpha * alpha), 0, 255);
        if (combinedAlpha <= 0) return;
        int color = (combinedAlpha << 24) | (baseColor & 0xFFFFFF);

        context.drawText(MinecraftClient.getInstance().textRenderer, lockChar,
                lx, ly, color, false);
    }

    private boolean externalOverlay;

    public void setExternalOverlay(boolean externalOverlay) {
        this.externalOverlay = externalOverlay;
    }

    public void renderOverlay(DrawContext context, int mouseX, int mouseY) {
        if (open) renderPicker(context, mouseX, mouseY);
    }

    private void renderPicker(DrawContext context, int mouseX, int mouseY) {
        int px = getPickerX();
        int py = getPickerY();
        int sbX = getSBX();
        int sbY = getSBY();
        int sbSize = getSBSize();
        int hueY = getHueY();
        int inputY = getInputY();

        cursorTick++;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);

        int pickerAlpha = (int) (this.alpha * 255);
        int bgAlpha = (int) (this.alpha * 89);
        int inputBgAlpha = (int) (this.alpha * 64);

        fillRoundedRect(context, px, py, PICKER_WIDTH, PICKER_HEIGHT, (bgAlpha << 24) | 0xFFFFFF);

        for (int i = 0; i < sbSize; i++) {
            float s_val = (float) i / sbSize;
            int colorTop = Color.HSBtoRGB(h, s_val, 1.0f);
            int colorBottom = Color.HSBtoRGB(h, s_val, 0.0f);
            context.fillGradient(sbX + i, sbY, sbX + i + 1, sbY + sbSize, (pickerAlpha << 24) | (colorTop & 0xFFFFFF), (pickerAlpha << 24) | (colorBottom & 0xFFFFFF));
        }

        int markerX = sbX + (int) (s * sbSize);
        int markerY = sbY + (int) ((1.0f - v) * sbSize);
        context.fill(markerX - 2, markerY - 2, markerX + 2, markerY + 2, (pickerAlpha << 24) | 0xFFFFFF);
        context.fill(markerX - 1, markerY - 1, markerX + 1, markerY + 1, (pickerAlpha << 24) | 0x000000);

        for (int i = 0; i < sbSize; i++) {
            float h_val = (float) i / sbSize;
            int hueColor = Color.HSBtoRGB(h_val, 1.0f, 1.0f);
            context.fill(sbX + i, hueY, sbX + i + 1, hueY + HUE_HEIGHT, (pickerAlpha << 24) | (hueColor & 0xFFFFFF));
        }

        int hMarkerX = sbX + (int) (h * sbSize);
        context.fill(hMarkerX - 1, hueY - 2, hMarkerX + 1, hueY + HUE_HEIGHT + 2, (pickerAlpha << 24) | 0xFFFFFF);

        fillRoundedRect(context, sbX, inputY, sbSize, INPUT_HEIGHT, (inputBgAlpha << 24) | 0x000000);
        if (inputFocused) {
            context.drawHorizontalLine(sbX, sbX + sbSize - 1, inputY, (pickerAlpha << 24) | 0xFFFFFF);
            context.drawHorizontalLine(sbX, sbX + sbSize - 1, inputY + INPUT_HEIGHT - 1, (pickerAlpha << 24) | 0xFFFFFF);
            context.drawVerticalLine(sbX, inputY, inputY + INPUT_HEIGHT - 1, (pickerAlpha << 24) | 0xFFFFFF);
            context.drawVerticalLine(sbX + sbSize - 1, inputY, inputY + INPUT_HEIGHT - 1, (pickerAlpha << 24) | 0xFFFFFF);
        }

        String displayText = hexInput;
        if (inputFocused && (cursorTick / 10) % 2 == 0) {
            displayText += "_";
        }
        
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 
                sbX + 2, inputY + 2, (pickerAlpha << 24) | 0xFFFFFF, false);

        context.getMatrices().pop();
    }

    private void fillRoundedRect(DrawContext context, int x, int y, int width, int height, int color) {
        int x1 = x;
        int y1 = y;
        int x2 = x + width;
        int y2 = y + height;
        context.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
        context.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        context.fill(x1, y1 + 2, x2, y2 - 2, color);
        context.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        context.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
        if (onExpandedChanged != null) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.USAGE,
                    Text.translatable(disclosureNarrationKey + (expanded ? ".collapse" : ".expand")));
        }
    }
}
