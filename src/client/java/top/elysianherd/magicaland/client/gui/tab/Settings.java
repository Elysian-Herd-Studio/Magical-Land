package top.elysianherd.magicaland.client.gui.tab;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import top.elysianherd.magicaland.client.gui.ConfigScreen;
import top.elysianherd.magicaland.client.gui.tab.settings.GeneralPage;
import top.elysianherd.magicaland.client.gui.tab.settings.GamePage;
import top.elysianherd.magicaland.client.gui.tab.settings.NetworkPage;
import top.elysianherd.magicaland.client.gui.tab.settings.SettingsPage;
import top.elysianherd.magicaland.client.gui.widget.HorizontalTabBar;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;

public class Settings implements TabContent {
    private SettingsList listWidget;
    private ConfigScreen screenRef;

    private static final int TAB_BAR_HEIGHT = 32;
    private static final int TAB_BAR_GAP = 8;
    private static final String[] TAB_KEYS = {
            "text.magicaland.config.tab.general",
            "text.magicaland.config.tab.game",
            "text.magicaland.config.tab.network"
    };
    private static final Text[] TAB_LABELS = {
            Text.translatable(TAB_KEYS[0]), Text.translatable(TAB_KEYS[1]), Text.translatable(TAB_KEYS[2])
    };
    private static final SettingsPage[] PAGES = {
            new GeneralPage(),
            new GamePage(),
            new NetworkPage()
    };
    private int selectedTab = 0;

    private int buttonX;
    private int buttonWidth;
    private int tabBarY;
    private final HorizontalTabBar tabBar = new HorizontalTabBar(TAB_LABELS, TAB_LABELS.length, 0, TAB_BAR_HEIGHT);

    @Override
    public void onEnter() {
        selectedTab = 0;
        tabBar.setSelected(0);
        tabBar.resetIndicator();
    }

    @Override
    public void init(ConfigScreen screen, int x, int y, int width, int height) {
        this.screenRef = screen;
        buttonWidth = Math.min(250, width - 20);
        buttonX = x + (width - buttonWidth) / 2;

        int topMargin = 30;
        int bottomMargin = 40;
        tabBarY = y + topMargin;

        int listTop = y + topMargin + TAB_BAR_HEIGHT + TAB_BAR_GAP;
        int listBottom = y + height - bottomMargin;

        listWidget = new SettingsList(MinecraftClient.getInstance(), width, height,
                listTop, listBottom, 24);
        listWidget.setLeftPos(x);

        int tabWidth = buttonWidth / TAB_KEYS.length;
        int totalWidth = tabWidth * TAB_KEYS.length;
        int startX = buttonX + (buttonWidth - totalWidth) / 2;
        tabBar.init(startX, tabBarY, tabWidth, selectedTab);

        PAGES[selectedTab].build(listWidget, buttonX, buttonWidth);

        screen.addConsoleElement(listWidget);
    }

    @Override
    public void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, float delta,
            float alpha) {
        tabBar.render(context, mouseX, mouseY, true);
        if (listWidget != null) {
            listWidget.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || screenRef == null) return false;

        return tabBar.mouseClicked(mouseX, mouseY, button, true, index -> {
            selectedTab = index;
            screenRef.reinitScreen();
        });
    }
}
