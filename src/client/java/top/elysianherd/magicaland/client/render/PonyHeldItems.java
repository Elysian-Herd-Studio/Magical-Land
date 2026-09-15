package top.elysianherd.magicaland.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import top.elysianherd.magicaland.client.config.ModelConfig;

/** 非魔法持物只影响模型展示，沿用玩家已有的主副手与挥动状态。 */
public final class PonyHeldItems {
    public enum Grip { NONE, MOUTH, HOOF, SHIELD }
    public record Consumption(Hand hand, UseAction action, float ticks) {
        public static final Consumption NONE = new Consumption(null, UseAction.NONE, 0);
        float pitch() {
            if (hand == null || (action != UseAction.EAT && action != UseAction.DRINK)) return 0;
            double phase = ticks * Math.PI * 2 / (action == UseAction.DRINK ? 10 : 8);
            return (float) Math.toRadians(action == UseAction.DRINK ? 28 + 2 * Math.sin(phase) : 24 + 3 * Math.sin(phase));
        }
    }
    public record Frame(AbstractClientPlayerEntity player, ItemStack main, ItemStack off,
                        Grip mainGrip, Grip offGrip, boolean mainLeft, float swing, Hand swingingHand,
                        Consumption consumption) {
        public static final Frame NONE = new Frame(null, ItemStack.EMPTY, ItemStack.EMPTY, Grip.NONE, Grip.NONE, false, 0, Hand.MAIN_HAND, Consumption.NONE);
        public boolean raises(boolean left) {
            return mainLeft == left && hoof(mainGrip) || mainLeft != left && hoof(offGrip);
        }
        public float mouthSwing() {
            return swingingHand == Hand.MAIN_HAND && mainGrip == Grip.MOUTH
                    || swingingHand == Hand.OFF_HAND && offGrip == Grip.MOUTH ? swing : 0;
        }
        float consumingPitch(boolean left) {
            boolean main = mainLeft == left;
            return (main ? mainGrip : offGrip) == Grip.HOOF
                    && consumption.hand() == (main ? Hand.MAIN_HAND : Hand.OFF_HAND) ? consumption.pitch() : 0;
        }
    }

    private PonyHeldItems() {}
    private static boolean hoof(Grip grip) { return grip == Grip.HOOF || grip == Grip.SHIELD; }
    public static boolean mouthItem(ItemStack stack) {
        Item item = stack.getItem();
        return !stack.isEmpty() && (item instanceof ToolItem || item instanceof TridentItem
                || item instanceof ShearsItem || item instanceof FishingRodItem || item instanceof FlintAndSteelItem
                || item instanceof BrushItem);
    }
    public static Grip grip(ItemStack stack, boolean mouthAvailable) {
        if (stack.isEmpty()) return Grip.NONE;
        if (stack.getItem() instanceof ShieldItem) return Grip.SHIELD;
        return mouthAvailable && mouthItem(stack) ? Grip.MOUTH : Grip.HOOF;
    }
    public static Frame frame(AbstractClientPlayerEntity player, ModelConfig config, float partialTick) {
        if (config == null || config.showHorn || player == null || !player.isAlive()
                || player.isInvisible() || player.isSpectator() || player.isSleeping()) return Frame.NONE;
        var main = player.getMainHandStack(); var off = player.getOffHandStack();
        Grip mainGrip = grip(main, true), offGrip = grip(off, mainGrip != Grip.MOUTH);
        return new Frame(player, main, off, mainGrip, offGrip, player.getMainArm() == Arm.LEFT,
                player.getHandSwingProgress(partialTick), player.preferredHand == null ? Hand.MAIN_HAND : player.preferredHand,
                consumption(player.isUsingItem(), player.getActiveHand(), player.getActiveItem(), player.getItemUseTimeLeft(), partialTick));
    }

    static Consumption consumption(boolean using, Hand hand, ItemStack stack, int timeLeft, float partialTick) {
        if (!using || hand == null || stack.isEmpty() || timeLeft <= 0) return Consumption.NONE;
        UseAction action = stack.getUseAction();
        if (action != UseAction.EAT && action != UseAction.DRINK) return Consumption.NONE;
        float partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        float ticks = Math.max(0, Math.min(stack.getMaxUseTime(), stack.getMaxUseTime() - timeLeft + partial));
        return new Consumption(hand, action, ticks);
    }

    static void renderAtBone(MatrixStack matrices, GeoBone bone, VertexConsumerProvider buffers,
                             Frame frame, int light, int overlay) {
        if (frame.player == null || bone.isHidden()) return;
        renderHand(matrices, bone, buffers, frame, frame.main, frame.mainGrip, frame.mainLeft, light, overlay, 0);
        renderHand(matrices, bone, buffers, frame, frame.off, frame.offGrip, !frame.mainLeft, light, overlay, 1);
    }

    private static void renderHand(MatrixStack matrices, GeoBone bone, VertexConsumerProvider buffers,
                                   Frame frame, ItemStack stack, Grip grip, boolean left, int light, int overlay, int hand) {
        if (grip == Grip.NONE) return;
        String anchor = grip == Grip.MOUTH ? "Head" : left ? "LFrontHoof" : "RFrontHoof";
        if (!anchor.equals(bone.getName())) return;
        matrices.push();
        try {
            RenderUtils.prepMatrixForBone(matrices, bone);
            if (grip == Grip.MOUTH) {
                matrices.translate(0, 22.05 / 16, -11.5 / 16);
                if (stack.getItem() instanceof TridentItem) {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(left ? -12 : 12));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left ? 90 : -90));
                    matrices.scale(.68f, .68f, .68f);
                    // NONE 使用三维三叉戟；抵消物品渲染偏移，让柄中段落在嘴边。
                    matrices.translate(.5, 1.40625, .5);
                } else {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(left ? -15 : 15));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left ? 25 : -25));
                    matrices.scale(.68f, .68f, .68f);
                    // 普通工具的柄靠近图标左下角，把柄而非图标中心放到嘴边。
                    matrices.translate(.25, .25, 0);
                }
            } else if (grip == Grip.SHIELD) {
                matrices.translate((bone.getPivotX() + (left ? -2.8 : 2.8)) / 16, 1.5 / 16, -3.5 / 16);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(left ? -90 : 90));
                matrices.scale(.65f, .65f, .65f);
            } else {
                matrices.translate(bone.getPivotX() / 16, -3.6 / 16, -3.5 / 16);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                matrices.scale(.45f, .45f, .45f);
            }
            MinecraftClient.getInstance().getItemRenderer().renderItem(frame.player, stack, ModelTransformationMode.NONE,
                    left, matrices, buffers, frame.player.getWorld(), light, overlay, frame.player.getId() * 2 + hand);
        } finally { matrices.pop(); }
    }
}
