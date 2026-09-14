package top.csituka.magicaland.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import top.csituka.magicaland.client.config.ModelManager;
import top.csituka.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.csituka.magicaland.client.gui.ponymanager.ModelGridWidget;
import top.csituka.magicaland.client.gui.widget.CustomButton;
import top.csituka.magicaland.client.network.MglSkinClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MglSkinScreen extends Screen {
    private final Screen parent;
    private List<MglSkinClient.RemoteSkin> skins = List.of();
    private Map<String, MglSkinClient.RemoteSkin> skinByKey = Map.of();
    private ModelGridWidget modelGrid;
    private String selectedKey = "";
    private String status = "";
    private boolean loading;
    private boolean loaded;
    private double listScroll;

    public MglSkinScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (modelGrid != null) listScroll = modelGrid.getScrollAmount();
        int contentWidth = Math.min(460, width - 24);
        int left = (width - contentWidth) / 2;
        addDrawableChild(new CustomButton(left, 10, 84, 20,
                text("refresh"), false, button -> refresh()));
        addDrawableChild(new CustomButton(left + contentWidth - 84, 10, 84, 20,
                text("back"), false, button -> close()));

        List<ModelGridWidget.ModelEntry> entries = new ArrayList<>();
        Map<String, MglSkinClient.RemoteSkin> byKey = new LinkedHashMap<>();
        for (MglSkinClient.RemoteSkin skin : skins) {
            String key = displayKey(skin, byKey);
            byKey.put(key, skin);
            entries.add(new ModelGridWidget.ModelEntry(key, MglSkinClient.parseModel(skin)));
        }
        skinByKey = Map.copyOf(byKey);
        if (!entries.isEmpty()) {
            Rect bounds = new Rect(left, 70, contentWidth, Math.max(20, height - 112));
            modelGrid = addDrawableChild(new ModelGridWidget(bounds, entries,
                    () -> selectedKey, this::selectSkin));
            modelGrid.restoreScrollAmount(listScroll);
        } else {
            modelGrid = null;
        }

        int actionY = height - 34;
        addDrawableChild(new CustomButton(left, actionY, 112, 20,
                MglSkinClient.isLoggedIn() ? text("logout") : text("login"), false,
                button -> { if (MglSkinClient.isLoggedIn()) logout(); else login(); }));
        CustomButton upload = new CustomButton(left + contentWidth - 112, actionY, 112, 20,
                text("upload"), false, button -> client.setScreen(new MglSkinUploadScreen(this)));
        upload.active = MglSkinClient.isLoggedIn() && ModelManager.getActiveModel() != null;
        addDrawableChild(upload);
        if (!loaded && !loading) refresh();
    }

    private String displayKey(MglSkinClient.RemoteSkin skin, Map<String, MglSkinClient.RemoteSkin> existing) {
        String base = skin.name().isBlank() ? "preset-" + skin.id() : skin.name();
        String key = base;
        if (existing.containsKey(key)) {
            String suffix = skin.username().isBlank() ? String.valueOf(skin.id()) : skin.username();
            key = base + " · " + suffix;
        }
        while (existing.containsKey(key)) key = base + " #" + skin.id();
        return key;
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        status = "";
        MglSkinClient.fetchSkins(result -> {
            skins = List.copyOf(result);
            loaded = true;
            loading = false;
            clearChildren();
            init();
        }, error -> {
            loading = false;
            status = error;
        });
    }

    private void selectSkin(String key) {
        selectedKey = key;
        MglSkinClient.RemoteSkin skin = skinByKey.get(key);
        if (skin != null) client.setScreen(new MglSkinDetailScreen(this, skin));
    }

    private void login() {
        status = text("opening_login").getString();
        MglSkinClient.beginLogin(username -> {
            status = Text.translatable("text.magicaland.mglskin.logged_in", username).getString();
            clearChildren();
            init();
        }, error -> status = error);
    }

    private void logout() {
        var config = top.csituka.magicaland.client.config.Config.getInstance();
        config.mglSkinToken = "";
        config.mglSkinUsername = "";
        top.csituka.magicaland.client.config.Config.save();
        clearChildren();
        init();
    }

    void uploadComplete(String name) {
        refresh();
        status = Text.translatable("text.magicaland.mglskin.uploaded", name).getString();
    }

    void uploadFailed(String error) {
        status = error;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        if (loading) {
            context.drawCenteredTextWithShadow(textRenderer, text("loading"), width / 2, 42, 0xFFAAAAAA);
        } else if (skins.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, text("empty"), width / 2, 42, 0xFFAAAAAA);
        }
        if (!status.isBlank()) {
            int contentWidth = Math.min(460, width - 24);
            var lines = textRenderer.wrapLines(Text.literal(status), contentWidth);
            int statusY = height - 58 - lines.size() * textRenderer.fontHeight;
            for (int i = 0; i < lines.size(); i++)
                context.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2,
                        statusY + i * textRenderer.fontHeight, 0xFFCCCCCC);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    private static Text text(String key) { return Text.translatable("text.magicaland.mglskin." + key); }
}
