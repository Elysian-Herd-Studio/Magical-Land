package top.elysianherd.magicaland.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.gui.ponycustom.*;
import top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout.Rect;
import top.elysianherd.magicaland.client.gui.widget.ColorPicker;
import top.elysianherd.magicaland.client.gui.widget.CustomButton;
import top.elysianherd.magicaland.client.gui.widget.HorizontalTabBar;
import top.elysianherd.magicaland.client.gui.widget.SettingsList;
import top.elysianherd.magicaland.client.gui.widget.ViewCube;
import top.elysianherd.magicaland.client.render.GlowingItem;

public class PonyCustom implements ViewCube.RotationTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(PonyCustom.class);
    private static final PonyCustomPage[] PAGES = {
            new ModelPage(), new MainPage(), new ManePage(), new FacePage(), new HornPage(), new BodyPage(), new GlowPage(), new CutieMarkPage()
    };
    private static final PonyCustomPageContext.Page[] CATEGORIES = {
            PonyCustomPageContext.Page.BODY, PonyCustomPageContext.Page.MANE, PonyCustomPageContext.Page.FACE,
            PonyCustomPageContext.Page.HORN, PonyCustomPageContext.Page.GLOW, PonyCustomPageContext.Page.CUTIE_MARK
    };
    private static final String[] CATEGORY_KEYS = { "body", "mane", "face", "horn", "glow", "cutie_mark" };
    private static final Text[] CATEGORY_LABELS = {
            Text.translatable("text.magicaland.customize.category.body"),
            Text.translatable("text.magicaland.customize.category.mane"),
            Text.translatable("text.magicaland.customize.category.face"),
            Text.translatable("text.magicaland.customize.category.horn"),
            Text.translatable("text.magicaland.customize.category.glow"),
            Text.translatable("text.magicaland.customize.category.cutie_mark")
    };
    private final double[] scrollPositions = new double[PAGES.length];
    private SettingsList listWidget;
    private PonyCustomPageContext pageContext;
    private CustomizationLayout layout;
    private int selectedPage = PonyCustomPageContext.Page.BODY.ordinal();
    private boolean refreshRequested;
    private PonyCustomPageContext.Page managementReturnPage = PonyCustomPageContext.Page.BODY;
    private boolean showGlowItem;
    private PonyPreviewRenderer ponyRenderer;
    private float previewYaw = 155.0f;
    private float previewPitch = -10.0f;
    private boolean isDraggingModel;
    private final PreviewCamera camera = new PreviewCamera();
    private PonyStylePart focusedPart;
    private Boolean focusedMarkSide;
    private boolean automaticFocus = true;
    private boolean snapCamera;
    private long lastPreviewFrame;
    private final HorizontalTabBar categoryBar = new HorizontalTabBar(CATEGORY_LABELS, 6, 4, 23);

    public void onEnter() {
        selectedPage = PonyCustomPageContext.Page.BODY.ordinal();
        refreshRequested = false;
        managementReturnPage = PonyCustomPageContext.Page.BODY;
        showGlowItem = false;
        isDraggingModel = false;
        focusedPart = null;
        focusedMarkSide = null;
        camera.reset();
        lastPreviewFrame = 0;
        categoryBar.setSelected(0);
        categoryBar.resetIndicator();
        resetCameraAngle();
        java.util.Arrays.fill(scrollPositions, 0);
        for (PonyCustomPage page : PAGES) page.onEnter();
    }

    public void onExit() {
        refreshRequested = false;
        isDraggingModel = false;
        currentPage().onLeave();
        ColorPicker.clearBodyLinkGroup();
        PonyStyleThumbnails.clear();
        if (ponyRenderer != null) ponyRenderer.close();
        ponyRenderer = null;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && layout != null && !currentPage().isEditingPreset()
                && categoryBar.mouseClicked(x, y, button, true, index -> switchPage(CATEGORIES[index], 0))) return true;
        if (currentPage().isEditingPreset() || layout == null || !isPreviewActive()
                || !layout.model().contains(x, y)) return false;
        if (button == 1) {
            resetCameraAngle();
            return true;
        }
        isDraggingModel = button == 0;
        return isDraggingModel;
    }

    public boolean mouseReleased(double x, double y, int button) {
        boolean dragging = isDraggingModel;
        if (button == 0) isDraggingModel = false;
        return dragging;
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !isDraggingModel) return false;
        setPreviewRotation(previewYaw - (float) dx * 0.5f, previewPitch - (float) dy * 0.5f);
        return true;
    }

    public boolean keyPressed(int key) {
        return currentPage().keyPressed(key);
    }

    public boolean hasPendingTextEdit() { return currentPage().isEditingPreset(); }

    public void endEditingSession() {
        for (PonyCustomPage page : PAGES) if (page instanceof CutieMarkPage marks) marks.releaseSession();
        listWidget = null;
        pageContext = null;
        layout = null;
    }

    @Override public float getPreviewYaw() { return previewYaw; }
    @Override public float getPreviewPitch() { return previewPitch; }
    @Override public void setPreviewRotation(float yaw, float pitch) {
        previewYaw = yaw;
        previewPitch = Math.max(-80, Math.min(80, pitch));
        camera.manual(previewYaw, previewPitch);
    }
    @Override public boolean isPreviewActive() { return ModelManager.getActiveModel() != null; }

    public void init(PonyCustomScreen screen, int x, int y, int width, int height) {
        isDraggingModel = false;
        ColorPicker.clearBodyLinkGroup();
        layout = CustomizationLayout.of(x, y, width, height);
        categoryBar.setColumns(layout.tabColumns());
        categoryBar.init(x, y, layout.tabWidth(), selectedCategory());
        initRenderer();
        Rect details = layout.details();
        pageContext = new PonyCustomPageContext(screen, details.x(), details.y(), details.width(), details.height(),
                this::switchPage, () -> refreshRequested = true, this::focusPart, () -> {
                    scrollPositions[selectedPage] = 0;
                    if (listWidget != null) listWidget.restoreScrollAmount(0);
                }, this::focusCutieMark);
        listWidget = new CustomizationList(MinecraftClient.getInstance(), details);
        currentPage().build(pageContext, listWidget);
        listWidget.restoreScrollAmount(scrollPositions[selectedPage]);
        screen.addConsoleElement(listWidget);
        buildPreviewControls(screen);
    }

    private void buildPreviewControls(PonyCustomScreen screen) {
        Rect panel = layout.preview();
        int x = panel.x() + 5;
        int y = panel.y() + 5;
        int width = panel.width() - 10;
        CustomButton focus = new CustomButton(x, y, width, 20,
                Text.literal(automaticFocus ? "☑ " : "☐ ").append(tr("preview.auto_focus")),
                false, button -> {
                    automaticFocus = !automaticFocus;
                    resetCameraAngle();
                    snapCamera = !automaticFocus;
                    pageContext.refreshKeepingScroll();
                });
        screen.addConsoleWidget(focus);
        if (currentPage().usesGlowPreview()) {
            screen.addConsoleWidget(new CustomButton(layout.model().x() + 2, layout.model().y() + 2,
                    Math.min(78, layout.model().width() - 4), 18,
                    tr(showGlowItem ? "preview.item" : "preview.pony"), false, button -> {
                        showGlowItem = !showGlowItem;
                        pageContext.refreshKeepingScroll();
                    }));
        }
        if (!automaticFocus && layout.model().width() >= 170 && layout.model().height() >= 140) {
            screen.addConsoleWidget(new ViewCube(layout.model().right() - 46, layout.model().y() + 2, 44, 44, this));
        }
    }

    private void applyPendingRefresh() {
        if (!refreshRequested) return;
        refreshRequested = false;
        scrollPositions[selectedPage] = listWidget.getScrollAmount();
        int focusedIndex = listWidget.children().indexOf(listWidget.getFocused());
        pageContext.reinit();
        if (!currentPage().isEditingPreset() && focusedIndex >= 0 && focusedIndex < listWidget.children().size()) {
            SettingsList.Entry entry = listWidget.children().get(focusedIndex);
            listWidget.setFocused(entry);
            entry.setFocused(entry.widget);
            pageContext.getScreen().setFocused(listWidget);
        }
    }

    private void switchPage(PonyCustomPageContext.Page page, int direction) {
        if (page == PonyCustomPageContext.Page.MAIN) page = managementReturnPage;
        if (pageContext == null || page.ordinal() == selectedPage) return;
        if (page == PonyCustomPageContext.Page.MODEL) managementReturnPage = PonyCustomPageContext.Page.values()[selectedPage];
        currentPage().onLeave();
        scrollPositions[selectedPage] = listWidget.getScrollAmount();
        selectedPage = page.ordinal();
        if (page == PonyCustomPageContext.Page.FACE) focusPart(PonyStylePart.EYE);
        else if (page == PonyCustomPageContext.Page.MANE) focusPart(((ManePage) currentPage()).selectedStylePart());
        else focusPart(null);
        if (page == PonyCustomPageContext.Page.GLOW) showGlowItem = true;
        refreshRequested = false;
        pageContext.reinit();
    }

    private PonyCustomPage currentPage() { return PAGES[selectedPage]; }
    private static Text tr(String key) { return Text.translatable("text.magicaland.customize." + key); }

    private void focusPart(PonyStylePart part) {
        if (focusedPart == part && focusedMarkSide == null) return;
        focusedPart = part;
        focusedMarkSide = null;
        if (automaticFocus) resetCameraAngle();
    }

    private void focusCutieMark(boolean left) {
        if (focusedMarkSide != null && focusedMarkSide == left) return;
        focusedPart = null;
        focusedMarkSide = left;
        if (automaticFocus) resetCameraAngle();
    }

    private void resetCameraAngle() {
        float yaw = 155, pitch = -10;
        if (automaticFocus && focusedMarkSide != null) {
            yaw = focusedMarkSide ? 75 : 285;
            pitch = 5;
        } else if (automaticFocus && focusedPart != null) {
            yaw = switch (focusedPart) { case FRONT_MANE -> 155; case EYE -> 145; case BACK_MANE -> 25; case TAIL -> 35; };
            pitch = focusedPart == PonyStylePart.TAIL ? 10 : focusedPart == PonyStylePart.BACK_MANE ? 6 : -8;
        }
        camera.angle(yaw, pitch);
        if (camera.pose() == null) { previewYaw = yaw; previewPitch = pitch; }
    }

    public void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY,
            float delta, float alpha) {
        if (layout == null) return;
        applyPendingRefresh();
        categoryBar.render(context, mouseX, mouseY, !currentPage().isEditingPreset());
        renderPreview(context, delta, mouseX, mouseY);
        currentPage().render(pageContext, context, mouseX, mouseY, delta, alpha);
        listWidget.render(context, mouseX, mouseY, delta);
        scrollPositions[selectedPage] = listWidget.getScrollAmount();
    }

    private int selectedCategory() {
        for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].ordinal() == selectedPage) return i;
        return -1;
    }

    private void renderPreview(DrawContext context, float delta, int mouseX, int mouseY) {
        ModelConfig config = ModelManager.getActiveModel();
        if (config == null || ponyRenderer == null) return;
        var player = MinecraftClient.getInstance().player;
        config = AppearanceAnatomy.apply(player == null ? null : player.getUuid(), config);
        Rect area = layout.model();
        try {
            var box = automaticFocus && focusedMarkSide != null ? PreviewGeometryBounds.cutieMarkFramingBounds(config)
                    : PreviewGeometryBounds.framingBounds(config, automaticFocus ? focusedPart : null);
            long now = System.nanoTime();
            double seconds = lastPreviewFrame == 0 ? 1.0 / 60 : (now - lastPreviewFrame) / 1.0e9;
            lastPreviewFrame = now;
            PreviewCamera.Pose pose = camera.update(new PreviewCamera.Box(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()), area.width(), area.height(), seconds, snapCamera);
            snapCamera = false;
            previewYaw = pose.yaw(); previewPitch = pose.pitch();
            if (currentPage().usesGlowPreview() && showGlowItem) {
                renderGrassBlockPreview(context, config, area);
            } else {
                ponyRenderer.renderPreview(context, config, area, pose, now, delta, mouseX, mouseY);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to render pony customization preview", e);
        }
    }

    private void initRenderer() {
        if (ponyRenderer == null) ponyRenderer = new PonyPreviewRenderer();
    }

    private void renderGrassBlockPreview(DrawContext context, ModelConfig config, Rect area) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack stack = new ItemStack(Blocks.GRASS_BLOCK);
        MatrixStack matrices = context.getMatrices();
        VertexConsumerProvider buffers = context.getVertexConsumers();
        context.draw();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        context.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        matrices.push();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            PreviewLightingRig.apply();
            float scale = Math.min(area.width() / 2.2f, area.height() / 2.2f);
            matrices.translate(area.x() + area.width() / 2f, area.y() + area.height() / 2f, 150);
            matrices.scale(scale, scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(previewYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(previewPitch));
            GlowingItem.renderPreviewWithGlow(client.getItemRenderer(), stack, ModelTransformationMode.NONE,
                    matrices, buffers, client.world, 0xF000F0, 0, GlowingItem.getGlowColor(config));
        } finally {
            try {
                context.draw();
            } finally {
                matrices.pop();
                context.disableScissor();
                DiffuseLighting.enableGuiDepthLighting();
                RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            }
        }
    }
}
