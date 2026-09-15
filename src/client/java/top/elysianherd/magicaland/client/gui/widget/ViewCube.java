package top.elysianherd.magicaland.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 视图立方体：朝向跟随捏马页面的小马模型，可拖动旋转，点击某个面切换到对应视角。
 * 立方体的轴与模型骨骼空间一致：-Z 为前、+Y 为上、-X 为小马的左侧。
 */
public class ViewCube extends ClickableWidget {

    /** 由分页实现，用于读写预览模型的朝向 */
    public interface RotationTarget {
        float getPreviewYaw();

        float getPreviewPitch();

        void setPreviewRotation(float yaw, float pitch);

        boolean isPreviewActive();
    }

    private static final float[][] NORMALS = {
            { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 }
    };

    private static final float[][][] CORNERS = {
            { { -1, 1, -1 }, { 1, 1, -1 }, { 1, 1, 1 }, { -1, 1, 1 } },
            { { -1, -1, -1 }, { -1, -1, 1 }, { 1, -1, 1 }, { 1, -1, -1 } },
            { { -1, -1, -1 }, { 1, -1, -1 }, { 1, 1, -1 }, { -1, 1, -1 } },
            { { -1, -1, 1 }, { -1, 1, 1 }, { 1, 1, 1 }, { 1, -1, 1 } },
            { { -1, -1, -1 }, { -1, 1, -1 }, { -1, 1, 1 }, { -1, -1, 1 } },
            { { 1, -1, -1 }, { 1, -1, 1 }, { 1, 1, 1 }, { 1, 1, -1 } }
    };

    private static final String[] LABEL_KEYS = {
            "text.magicaland.config.view_cube.up",
            "text.magicaland.config.view_cube.down",
            "text.magicaland.config.view_cube.front",
            "text.magicaland.config.view_cube.back",
            "text.magicaland.config.view_cube.left",
            "text.magicaland.config.view_cube.right"
    };

    // 各面正对镜头时的视角，yaw 为 NaN 表示按当前朝向就近取正前或正后
    private static final float[] SNAP_YAW = { Float.NaN, Float.NaN, 180.0f, 0.0f, 90.0f, 270.0f };
    private static final float[] SNAP_PITCH = { 90.0f, -90.0f, 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float DRAG_SENSITIVITY = 0.5f;
    private static final float INSET = 0.84f;
    private static final float BASE_Z = 60.0f;
    private static final float LABEL_Z = 120.0f;
    private static final float MIN_LABEL_FACING = 0.25f;

    private final RotationTarget target;

    private final float[][] faceX = new float[6][4];
    private final float[][] faceY = new float[6][4];
    private final float[][] faceZ = new float[6][4];
    private final float[] facing = new float[6];
    private final boolean[] faceVisible = new boolean[6];

    private int hoveredFace = -1;
    private int pressedFace = -1;
    private boolean dragging = false;
    private double pressX;
    private double pressY;
    private float fadeAlpha = 0.0f;

    private boolean snapping = false;
    private float snapYaw;
    private float snapPitch;
    private float appliedYaw;
    private float appliedPitch;

    public ViewCube(int x, int y, int width, int height, RotationTarget target) {
        super(x, y, width, height, Text.translatable("text.magicaland.config.view_cube.name"));
        this.target = target;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.visible = this.target != null && this.target.isPreviewActive();
        if (!this.visible) {
            this.fadeAlpha = 0.0f;
            this.hoveredFace = -1;
            this.snapping = false;
            return;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        updateSnap();
        this.fadeAlpha = Math.min(1.0f, this.fadeAlpha + 0.12f);
        project();
        this.hoveredFace = this.isHovered() ? faceAt(mouseX, mouseY) : -1;
        drawFaces(context);
        drawLabels(context);
    }

    /** 把立方体各面变换到与模型相同的朝向，再正交投影到屏幕 */
    private void project() {
        float yaw = this.target.getPreviewYaw();
        float pitch = this.target.getPreviewPitch();

        Matrix4f rotation = new Matrix4f()
                .rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch));

        float radius = Math.min(this.width, this.height) * 0.28f;
        float centerX = this.getX() + this.width / 2.0f;
        float centerY = this.getY() + this.height / 2.0f;

        Vector3f vec = new Vector3f();
        for (int face = 0; face < 6; face++) {
            vec.set(NORMALS[face][0], NORMALS[face][1], NORMALS[face][2]);
            rotation.transformPosition(vec);
            this.facing[face] = vec.z;
            this.faceVisible[face] = vec.z > 0.01f;
            if (!this.faceVisible[face]) {
                continue;
            }
            for (int corner = 0; corner < 4; corner++) {
                vec.set(CORNERS[face][corner][0], CORNERS[face][corner][1], CORNERS[face][corner][2]);
                rotation.transformPosition(vec);
                this.faceX[face][corner] = centerX + vec.x * radius;
                this.faceY[face][corner] = centerY + vec.y * radius;
                this.faceZ[face][corner] = BASE_Z + vec.z * radius;
            }
        }
    }

    private void drawFaces(DrawContext context) {
        VertexConsumer consumer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        float[] insetX = new float[4];
        float[] insetY = new float[4];
        float[] insetZ = new float[4];
        float[] edgeX = new float[4];
        float[] edgeY = new float[4];
        float[] edgeZ = new float[4];

        for (int face = 0; face < 6; face++) {
            if (!this.faceVisible[face]) {
                continue;
            }
            boolean hovered = this.hoveredFace == face;
            float shade = this.facing[face];
            int fillAlpha = (int) ((hovered ? 0.55f : 0.12f + 0.18f * shade) * this.fadeAlpha * 255);
            int edgeAlpha = (int) ((hovered ? 0.90f : 0.35f + 0.25f * shade) * this.fadeAlpha * 255);

            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < 4; i++) {
                cx += this.faceX[face][i] / 4.0f;
                cy += this.faceY[face][i] / 4.0f;
                cz += this.faceZ[face][i] / 4.0f;
            }
            for (int i = 0; i < 4; i++) {
                insetX[i] = cx + (this.faceX[face][i] - cx) * INSET;
                insetY[i] = cy + (this.faceY[face][i] - cy) * INSET;
                insetZ[i] = cz + (this.faceZ[face][i] - cz) * INSET;
            }

            drawQuad(consumer, matrix, insetX, insetY, insetZ, (fillAlpha << 24) | 0xFFFFFF);

            for (int i = 0; i < 4; i++) {
                int next = (i + 1) & 3;
                edgeX[0] = this.faceX[face][i];
                edgeY[0] = this.faceY[face][i];
                edgeZ[0] = this.faceZ[face][i];
                edgeX[1] = this.faceX[face][next];
                edgeY[1] = this.faceY[face][next];
                edgeZ[1] = this.faceZ[face][next];
                edgeX[2] = insetX[next];
                edgeY[2] = insetY[next];
                edgeZ[2] = insetZ[next];
                edgeX[3] = insetX[i];
                edgeY[3] = insetY[i];
                edgeZ[3] = insetZ[i];
                drawQuad(consumer, matrix, edgeX, edgeY, edgeZ, (edgeAlpha << 24) | 0xFFFFFF);
            }
        }

        context.draw();
    }

    /** 面朝向不确定，按投影后的绕向补正顶点顺序，避免被背面剔除 */
    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix, float[] x, float[] y, float[] z, int color) {
        float area = 0;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            area += x[i] * y[next] - x[next] * y[i];
        }
        if (area <= 0) {
            for (int i = 0; i < 4; i++) {
                consumer.vertex(matrix, x[i], y[i], z[i]).color(color).next();
            }
        } else {
            for (int i = 3; i >= 0; i--) {
                consumer.vertex(matrix, x[i], y[i], z[i]).color(color).next();
            }
        }
    }

    private void drawLabels(DrawContext context) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        for (int face = 0; face < 6; face++) {
            if (!this.faceVisible[face] || this.facing[face] < MIN_LABEL_FACING) {
                continue;
            }
            Text label = Text.translatable(LABEL_KEYS[face]);
            int textWidth = textRenderer.getWidth(label);
            if (textWidth <= 0) {
                continue;
            }

            float cx = 0, cy = 0;
            for (int i = 0; i < 4; i++) {
                cx += this.faceX[face][i] / 4.0f;
                cy += this.faceY[face][i] / 4.0f;
            }

            float scale = MathHelper.clamp(1.7f * innerRadius(face, cx, cy) / textWidth, 0.45f, 1.0f);
            int alpha = (int) ((0.45f + 0.55f * this.facing[face]) * this.fadeAlpha * 255);
            if (alpha <= 0) {
                continue;
            }

            context.getMatrices().push();
            context.getMatrices().translate(cx, cy, LABEL_Z);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.drawCenteredTextWithShadow(textRenderer, label, 0, -4, (alpha << 24) | 0xFFFFFF);
            context.getMatrices().pop();
        }
    }

    /** 面心到四条边的最短距离，用于把标签缩到面内 */
    private float innerRadius(int face, float cx, float cy) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            float ex = this.faceX[face][next] - this.faceX[face][i];
            float ey = this.faceY[face][next] - this.faceY[face][i];
            float length = MathHelper.sqrt(ex * ex + ey * ey);
            if (length < 0.001f) {
                continue;
            }
            float distance = Math.abs(ex * (cy - this.faceY[face][i]) - ey * (cx - this.faceX[face][i])) / length;
            min = Math.min(min, distance);
        }
        return min == Float.MAX_VALUE ? 0.0f : min;
    }

    private int faceAt(double mouseX, double mouseY) {
        int result = -1;
        float bestDepth = -Float.MAX_VALUE;
        for (int face = 0; face < 6; face++) {
            if (!this.faceVisible[face] || !containsPoint(face, mouseX, mouseY)) {
                continue;
            }
            float depth = 0;
            for (int i = 0; i < 4; i++) {
                depth += this.faceZ[face][i];
            }
            if (depth > bestDepth) {
                bestDepth = depth;
                result = face;
            }
        }
        return result;
    }

    private boolean containsPoint(int face, double px, double py) {
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            double cross = (this.faceX[face][next] - this.faceX[face][i]) * (py - this.faceY[face][i])
                    - (this.faceY[face][next] - this.faceY[face][i]) * (px - this.faceX[face][i]);
            if (cross > 0) {
                positive = true;
            } else if (cross < 0) {
                negative = true;
            }
        }
        return !(positive && negative);
    }

    private void updateSnap() {
        float yaw = this.target.getPreviewYaw();
        float pitch = this.target.getPreviewPitch();

        // 模型被其他方式转动时放弃这次吸附
        if (this.snapping && (Math.abs(MathHelper.wrapDegrees(yaw - this.appliedYaw)) > 0.01f
                || Math.abs(pitch - this.appliedPitch) > 0.01f)) {
            this.snapping = false;
        }

        if (this.snapping) {
            float deltaYaw = MathHelper.wrapDegrees(this.snapYaw - yaw);
            float deltaPitch = this.snapPitch - pitch;
            if (Math.abs(deltaYaw) < 0.5f && Math.abs(deltaPitch) < 0.5f) {
                yaw = this.snapYaw;
                pitch = this.snapPitch;
                this.snapping = false;
            } else {
                yaw += deltaYaw * 0.3f;
                pitch += deltaPitch * 0.3f;
            }
            this.target.setPreviewRotation(yaw, pitch);
        }

        this.appliedYaw = this.target.getPreviewYaw();
        this.appliedPitch = this.target.getPreviewPitch();
    }

    private void snapTo(int face) {
        float yaw = SNAP_YAW[face];
        float pitch = SNAP_PITCH[face];
        if (Float.isNaN(yaw)) {
            // 俯仰绕的是模型自身的 X 轴，只有 yaw 正对前后时上下面才会朝向镜头，
            // 且朝后时俯仰方向相反。取较近的一侧，避免大幅度转动。
            boolean reversed = Math.abs(MathHelper.wrapDegrees(this.target.getPreviewYaw())) > 90.0f;
            yaw = reversed ? 180.0f : 0.0f;
            if (reversed) {
                pitch = -pitch;
            }
        }
        this.snapYaw = yaw;
        this.snapPitch = pitch;
        this.snapping = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active || button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.pressedFace = faceAt(mouseX, mouseY);
        this.pressX = mouseX;
        this.pressY = mouseY;
        this.dragging = false;
        this.snapping = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!this.visible || button != 0) {
            return false;
        }
        if (!this.dragging && (Math.abs(mouseX - this.pressX) > 2 || Math.abs(mouseY - this.pressY) > 2)) {
            this.dragging = true;
        }
        if (this.dragging) {
            this.target.setPreviewRotation(
                    this.target.getPreviewYaw() - (float) deltaX * DRAG_SENSITIVITY,
                    this.target.getPreviewPitch() - (float) deltaY * DRAG_SENSITIVITY);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.dragging && this.pressedFace >= 0 && this.visible) {
            snapTo(this.pressedFace);
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
        }
        if (button == 0) {
            this.pressedFace = -1;
            this.dragging = false;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
