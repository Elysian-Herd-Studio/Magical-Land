package top.csituka.magicaland.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.csituka.magicaland.client.config.Config;
import top.csituka.magicaland.client.gui.widget.CustomButton;
import top.csituka.magicaland.client.network.MglSkinClient;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public final class MglSkinAccountScreen extends Screen {
    private final MglCloudPresetScreen parent;
    private final String accountToken, serviceUrl;
    private String username;
    private Identifier avatarTexture;
    private CustomButton refreshButton;
    private Text status = Text.empty();
    private int requestVersion;
    private boolean loaded, loading;

    public MglSkinAccountScreen(MglCloudPresetScreen parent) {
        super(text("account_title"));
        this.parent = parent;
        Config config = Config.getInstance();
        accountToken = config.mglSkinToken;
        serviceUrl = config.mglSkinUrl;
        username = Objects.requireNonNullElse(MglSkinClient.username(), "");
    }

    @Override
    protected void init() {
        super.init();
        if (!sameAccount()) { close(); return; }
        int contentWidth = Math.min(360, width - 24);
        int left = (width - contentWidth) / 2;
        refreshButton = addDrawableChild(new CustomButton(left, 6, 64, 20,
                text("refresh"), false, button -> refresh()));
        refreshButton.active = !loading;
        addDrawableChild(new CustomButton(left + contentWidth - 64, 6, 64, 20,
                text("back"), false, button -> close()));
        int formWidth = Math.min(280, width - 32);
        int formLeft = (width - formWidth) / 2;
        addDrawableChild(new CustomButton(formLeft, avatarY() + 96, formWidth, 24,
                text("account_edit"), false, button -> openEditor()));
        addDrawableChild(new CustomButton(formLeft, avatarY() + 124, formWidth, 24,
                text("logout"), false, button -> confirmLogout()));
        if (!loaded) refresh();
    }

    private boolean sameAccount() {
        Config config = Config.getInstance();
        return MglSkinClient.isLoggedIn() && Objects.equals(accountToken, config.mglSkinToken)
                && Objects.equals(serviceUrl, config.mglSkinUrl);
    }

    private boolean currentRequest(int version) {
        return version == requestVersion && client != null && client.currentScreen == this && sameAccount();
    }

    private void refresh() {
        if (loading) return;
        if (!sameAccount()) { close(); return; }
        loaded = loading = true;
        status = text("account_loading");
        refreshButton.active = false;
        clearAvatar();
        int version = ++requestVersion;
        MglSkinClient.fetchAccount(profile -> {
            if (!currentRequest(version)) return;
            username = profile.username();
            Config config = Config.getInstance();
            if (!Objects.equals(config.mglSkinUsername, username)) {
                config.mglSkinUsername = username;
                Config.save();
            }
            if (profile.hasAvatar()) {
                MglSkinClient.fetchAvatar(data -> {
                    if (!currentRequest(version)) return;
                    finishLoad(loadAvatar(data) ? Text.empty() : text("avatar_error"));
                }, error -> {
                    if (currentRequest(version)) finishLoad(Text.literal(error));
                });
            } else finishLoad(Text.empty());
        }, error -> {
            if (currentRequest(version)) finishLoad(Text.literal(error));
        });
    }

    private void finishLoad(Text message) {
        loading = false;
        status = message;
        refreshButton.active = true;
    }

    private boolean loadAvatar(byte[] data) {
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        Identifier registered = null;
        try {
            image = NativeImage.read(data);
            if (image.getWidth() != 128 || image.getHeight() != 128)
                throw new IOException("Invalid avatar dimensions");
            texture = new NativeImageBackedTexture(image);
            image = null;
            registered = client.getTextureManager().registerDynamicTexture("magicaland_account_avatar", texture);
            texture.upload();
            texture.setFilter(true, false);
            avatarTexture = registered;
            return true;
        } catch (IOException | RuntimeException error) {
            if (registered != null) client.getTextureManager().destroyTexture(registered);
            else if (texture != null) texture.close();
            else if (image != null) image.close();
            return false;
        }
    }

    private void clearAvatar() {
        if (avatarTexture == null) return;
        client.getTextureManager().destroyTexture(avatarTexture);
        avatarTexture = null;
    }

    private void openEditor() {
        if (!sameAccount()) { close(); return; }
        status = Text.empty();
        MglSkinClient.openAccountPage(error -> status = Text.literal(error));
    }

    private void confirmLogout() {
        if (!sameAccount()) { close(); return; }
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (!sameAccount()) { close(); return; }
            if (confirmed) {
                MglSkinClient.logout();
                close();
            } else client.setScreen(this);
        }, text("logout_title"), text("logout_confirm"), text("logout"),
                Text.translatable("text.magicaland.config.button.cancel")));
    }

    @Override
    public void tick() {
        super.tick();
        if (!sameAccount()) close();
    }

    @Override
    public void removed() {
        requestVersion++;
        loaded = loading = false;
        clearAvatar();
        super.removed();
    }

    private int avatarY() { return Math.max(38, (height - 182) / 2); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        int formWidth = Math.min(280, width - 32);
        int avatarY = avatarY();
        int avatarX = width / 2 - 32;
        context.fill(avatarX, avatarY, avatarX + 64, avatarY + 64, 0x66444444);
        if (avatarTexture != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(avatarTexture, avatarX, avatarY, 64, 64, 0, 0, 128, 128, 128, 128);
            RenderSystem.disableBlend();
        } else {
            String initial = username.isBlank() ? "?"
                    : username.substring(0, username.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            context.getMatrices().push();
            context.getMatrices().translate(width / 2.0, avatarY + 32, 0);
            context.getMatrices().scale(2, 2, 1);
            context.drawCenteredTextWithShadow(textRenderer, initial, 0, -textRenderer.fontHeight / 2, 0xFFFFFFFF);
            context.getMatrices().pop();
        }
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(username, formWidth),
                width / 2, avatarY + 74, 0xFFFFFFFF);
        int statusY = avatarY + 160;
        var lines = textRenderer.wrapLines(status, formWidth);
        int visibleLines = Math.max(0, (height - statusY - 8) / textRenderer.fontHeight);
        for (int i = 0; i < Math.min(visibleLines, lines.size()); i++) context.drawCenteredTextWithShadow(textRenderer,
                lines.get(i), width / 2, statusY + i * textRenderer.fontHeight, 0xFFAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }

    private static Text text(String key, Object... args) {
        return Text.translatable("text.magicaland.mglskin." + key, args);
    }
}
