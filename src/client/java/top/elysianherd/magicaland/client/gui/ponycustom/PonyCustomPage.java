package top.elysianherd.magicaland.client.gui.ponycustom;

import net.minecraft.client.gui.DrawContext;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

public interface PonyCustomPage {
    default boolean keyPressed(int key) { return false; }
    default boolean isEditingPreset() { return false; }
    void build(PonyCustomPageContext context, SettingsList list);

    default void onEnter() {}
    default void onLeave() {}

    default void render(PonyCustomPageContext context, DrawContext drawContext, int mouseX, int mouseY,
            float delta, float alpha) {}

    default boolean isPreviewActive() {
        return true;
    }

    default boolean usesGlowPreview() {
        return false;
    }
}
