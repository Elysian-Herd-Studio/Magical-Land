package top.csituka.magicaland.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import top.csituka.magicaland.client.config.Config;
import top.csituka.magicaland.client.config.ModelManager;
import top.csituka.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.csituka.magicaland.client.gui.ponymanager.ModelGridWidget;
import top.csituka.magicaland.client.gui.widget.CustomButton;
import top.csituka.magicaland.client.network.MglSkinClient;
import top.csituka.magicaland.client.network.MglSkinClient.RemoteSkin;

import java.util.List;
import java.util.Objects;

public final class MglCloudPresetScreen extends Screen {
    private final Screen parent;
    private List<RemoteSkin> skins = List.of();
    private ModelGridWidget modelGrid;
    private CustomButton refreshButton, sessionButton, detailsButton, visibilityButton, uploadButton;
    private CustomButton previousButton, nextButton;
    private String selectedKey = "";
    private String accountToken, serviceUrl;
    private Text status = Text.empty();
    private int page = 1;
    private int pages = 1;
    private int requestVersion;
    private boolean loaded, connected, loading, changingVisibility, openingLogin, loginRequired;
    private double listScroll;

    public MglCloudPresetScreen(Screen parent) {
        super(text("cloud_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (modelGrid != null) listScroll = modelGrid.getScrollAmount();
        if (!sameAccount()) {
            status = Text.empty();
            resetAccount();
        }
        loginRequired = !MglSkinClient.isLoggedIn();
        if (loginRequired) {
            initLogin();
            return;
        }
        int contentWidth = Math.min(520, width - 24);
        int left = (width - contentWidth) / 2;
        refreshButton = addDrawableChild(new CustomButton(left, 6, 64, 20,
                text("refresh"), false, button -> refresh(page)));
        addDrawableChild(new CustomButton(left + contentWidth - 64, 6, 64, 20,
                text("back"), false, button -> close()));
        addDrawableChild(new CustomButton(left, 30, 112, 20,
                text("title"), false, button -> client.setScreen(new MglSkinScreen(this))));
        sessionButton = addDrawableChild(new CustomButton(left + contentWidth - 112, 30, 112, 20,
                text("account_title"), false, button -> client.setScreen(new MglSkinAccountScreen(this))));

        var entries = skins.stream().map(skin -> new ModelGridWidget.ModelEntry(
                Long.toString(skin.id()), skin.name(), MglSkinClient.parseModel(skin),
                text(skin.isPublic() ? "public" : "private"))).toList();
        modelGrid = entries.isEmpty() ? null : addDrawableChild(new ModelGridWidget(
                new Rect(left, 54, contentWidth, Math.max(20, height - 140)), entries,
                () -> selectedKey, key -> { selectedKey = key; updateButtons(); }));
        if (modelGrid != null) modelGrid.restoreScrollAmount(listScroll);

        int half = (contentWidth - 4) / 2;
        detailsButton = addDrawableChild(new CustomButton(left, height - 80, half, 20,
                text("details"), false, button -> {
                    RemoteSkin skin = selectedSkin();
                    if (skin != null) client.setScreen(new MglSkinDetailScreen(this, skin));
                }));
        visibilityButton = addDrawableChild(new CustomButton(left + half + 4, height - 80,
                contentWidth - half - 4, 20, text("make_public"), false, button -> changeVisibility()));
        previousButton = addDrawableChild(new CustomButton(left, height - 32, 28, 20,
                Text.literal("<"), false, button -> refresh(page - 1)));
        nextButton = addDrawableChild(new CustomButton(left + 120, height - 32, 28, 20,
                Text.literal(">"), false, button -> refresh(page + 1)));
        previousButton.setTooltip(Tooltip.of(text("previous_page")));
        nextButton.setTooltip(Tooltip.of(text("next_page")));
        uploadButton = addDrawableChild(new CustomButton(left + contentWidth - 112, height - 32, 112, 20,
                text("upload"), false, button -> client.setScreen(new MglSkinUploadScreen(this))));
        updateButtons();
        if (!loaded) refresh(1);
    }

    private void initLogin() {
        modelGrid = null;
        refreshButton = null;
        int formWidth = Math.min(280, width - 32);
        int left = (width - formWidth) / 2;
        int actionY = height / 2;
        sessionButton = addDrawableChild(new CustomButton(left, actionY, formWidth, 24,
                text("login"), false, button -> login()));
        int half = (formWidth - 4) / 2;
        addDrawableChild(new CustomButton(left, actionY + 30, half, 20,
                text("title"), false, button -> client.setScreen(new MglSkinScreen(this))));
        addDrawableChild(new CustomButton(left + half + 4, actionY + 30, formWidth - half - 4, 20,
                text("back"), false, button -> close()));
        updateButtons();
        setInitialFocus(sessionButton);
    }

    private boolean sameAccount() {
        Config config = Config.getInstance();
        return Objects.equals(accountToken, config.mglSkinToken) && Objects.equals(serviceUrl, config.mglSkinUrl);
    }

    private void resetAccount() {
        Config config = Config.getInstance();
        accountToken = config.mglSkinToken;
        serviceUrl = config.mglSkinUrl;
        requestVersion++;
        skins = List.of();
        modelGrid = null;
        selectedKey = "";
        listScroll = 0;
        page = pages = 1;
        loaded = connected = loading = changingVisibility = false;
    }

    private RemoteSkin selectedSkin() {
        return skins.stream().filter(skin -> Long.toString(skin.id()).equals(selectedKey)).findFirst().orElse(null);
    }

    private void updateButtons() {
        if (sessionButton != null) sessionButton.active = !changingVisibility && !openingLogin;
        if (refreshButton == null) return;
        boolean idle = !loading && !changingVisibility;
        boolean loggedIn = MglSkinClient.isLoggedIn();
        RemoteSkin selected = selectedSkin();
        refreshButton.active = idle && loggedIn;
        detailsButton.active = idle && selected != null;
        visibilityButton.active = idle && loggedIn && selected != null;
        visibilityButton.setMessage(text(selected != null && selected.isPublic() ? "make_private" : "make_public"));
        uploadButton.active = idle && loggedIn && connected && !ModelManager.getAvailableModels().isEmpty();
        previousButton.active = idle && loggedIn && page > 1;
        nextButton.active = idle && loggedIn && page < pages;
        if (modelGrid != null) modelGrid.active = idle;
    }

    private void refresh(int requestedPage) {
        if (loading || changingVisibility || !MglSkinClient.isLoggedIn()) return;
        loaded = loading = true;
        status = Text.empty();
        int version = ++requestVersion;
        updateButtons();
        MglSkinClient.fetchCloudSkins(requestedPage, result -> {
            if (version != requestVersion || !sameAccount()) return;
            if (page != result.page()) {
                modelGrid = null;
                listScroll = 0;
                selectedKey = "";
            }
            skins = result.items();
            page = result.page();
            pages = result.pages();
            if (selectedSkin() == null) selectedKey = "";
            loading = false;
            connected = true;
            rebuild();
        }, error -> {
            if (version != requestVersion || !sameAccount()) return;
            loading = false;
            connected = false;
            status = Text.literal(error);
            updateButtons();
        });
    }

    private void changeVisibility() {
        RemoteSkin selected = selectedSkin();
        if (selected == null || loading || changingVisibility || !MglSkinClient.isLoggedIn()) return;
        changingVisibility = true;
        status = text("saving_visibility");
        int version = ++requestVersion;
        updateButtons();
        MglSkinClient.setPublic(selected.id(), !selected.isPublic(), isPublic -> {
            if (version != requestVersion || !sameAccount()) return;
            skins = skins.stream().map(skin -> skin.id() == selected.id()
                    ? new RemoteSkin(skin.id(), skin.name(), skin.username(), skin.data(), isPublic) : skin).toList();
            changingVisibility = false;
            status = text(isPublic ? "published" : "unpublished", selected.name());
            invalidatePublicList();
            rebuild();
        }, error -> {
            if (version != requestVersion || !sameAccount()) return;
            changingVisibility = false;
            status = Text.literal(error);
            updateButtons();
        });
    }

    private void login() {
        if (MglSkinClient.isLoggedIn() || openingLogin) return;
        openingLogin = true;
        status = text("opening_login");
        updateButtons();
        MglSkinClient.beginLogin(username -> {
            openingLogin = false;
            status = text("logged_in", username);
            resetAccount();
            rebuild();
        }, error -> {
            openingLogin = false;
            status = Text.literal(error);
            updateButtons();
        });
    }

    void uploadComplete(String name) {
        invalidatePublicList();
        refresh(1);
        status = text("uploaded", name);
    }

    void uploadFailed(String error) { status = Text.literal(error); }

    private void invalidatePublicList() {
        if (parent instanceof MglSkinScreen shared) shared.invalidate();
    }

    private void rebuild() {
        if (client == null || client.currentScreen != this) return;
        clearChildren();
        init();
    }

    @Override
    public void tick() {
        super.tick();
        if (!sameAccount()) {
            status = Text.empty();
            rebuild();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        if (loginRequired) {
            renderLogin(context);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        int contentWidth = Math.min(520, width - 24);
        int left = (width - contentWidth) / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        if (skins.isEmpty() && (loading || connected)) context.drawCenteredTextWithShadow(textRenderer,
                loading ? text("loading") : text("cloud_empty"),
                width / 2, 54 + Math.max(20, height - 140) / 2 - 4, 0xFFAAAAAA);
        var lines = textRenderer.wrapLines(status, contentWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) context.drawCenteredTextWithShadow(textRenderer,
                lines.get(i), width / 2, height - 56 + i * textRenderer.fontHeight, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(textRenderer, text("page", page, pages), left + 74, height - 26, 0xFFCCCCCC);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderLogin(DrawContext context) {
        int formWidth = Math.min(280, width - 32);
        int actionY = height / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, actionY - 48, 0xFFFFFFFF);
        var hint = textRenderer.wrapLines(text("cloud_login_hint"), formWidth);
        for (int i = 0; i < hint.size(); i++) context.drawCenteredTextWithShadow(textRenderer,
                hint.get(i), width / 2, actionY - 28 + i * textRenderer.fontHeight, 0xFFAAAAAA);
        var lines = textRenderer.wrapLines(status, formWidth);
        int visibleLines = Math.max(0, (height - actionY - 72) / textRenderer.fontHeight);
        for (int i = 0; i < Math.min(visibleLines, lines.size()); i++) context.drawCenteredTextWithShadow(textRenderer,
                lines.get(i), width / 2, actionY + 64 + i * textRenderer.fontHeight, 0xFFCCCCCC);
    }

    @Override public void close() { client.setScreen(parent); }

    private static Text text(String key, Object... args) {
        return Text.translatable("text.magicaland.mglskin." + key, args);
    }
}
