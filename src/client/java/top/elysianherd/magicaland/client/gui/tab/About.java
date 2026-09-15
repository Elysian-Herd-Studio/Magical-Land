package top.elysianherd.magicaland.client.gui.tab;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.elysianherd.magicaland.client.gui.ConfigScreen;

public class About implements TabContent {

    private static final Identifier ICON = new Identifier("magicaland", "icon-full.png");
    private static final String VERSION = FabricLoader.getInstance().getModContainer("magicaland")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("Unknown");

    @Override
    public void init(ConfigScreen screen, int x, int y, int width, int height) {
        // 滚木
    }

    @Override
    public void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, float delta,
            float alpha) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int iconWidth = 128;
        int iconHeight = 64;
        int iconX = x + (width - iconWidth) / 2;
        int iconY = y + (height - iconHeight - 40) / 2;

        context.drawTexture(ICON, iconX, iconY, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);

        Text descriptionText = Text.translatable("text.magicaland.console.about.description");
        int descriptionX = x + (width - textRenderer.getWidth(descriptionText)) / 2;
        int descriptionY = iconY + iconHeight + 10;
        context.drawTextWithShadow(textRenderer, descriptionText, descriptionX, descriptionY, 0xFFFFFF);

        Text versionText = Text.literal("Version " + VERSION);
        int versionX = x + (width - textRenderer.getWidth(versionText)) / 2;
        int versionY = descriptionY + textRenderer.fontHeight + 5;
        context.drawTextWithShadow(textRenderer, versionText, versionX, versionY, 0xAAAAAA);
    }
}
