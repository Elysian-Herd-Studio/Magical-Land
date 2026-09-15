package top.elysianherd.magicaland.client.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** 旧版护甲按当前骨骼逐段绘制，不再执行另一套身体动画。 */
public final class PonyArmorRenderer {
    private static final Identifier METAL = id("geo/armor/metal.geo.json");
    private static final Identifier LEATHER = id("geo/armor/leather.geo.json");
    private static final Identifier TURTLE = id("geo/armor/turtle.geo.json");
    private static final Identifier TURTLE_TEXTURE = new Identifier("minecraft", "textures/models/armor/turtle_layer_1.png");

    private PonyArmorRenderer() {}

    public static void init() {
        PonyArmorTextures.init();
        PonyArmorAutoColors.init();
    }

    static boolean hidesBone(AbstractClientPlayerEntity player, String bone) {
        if (player == null) return false;
        if ("BackMane".equals(bone)) {
            for (ItemStack stack : player.getArmorItems()) {
                if (stack.getItem() instanceof ArmorItem) return true;
            }
            return false;
        }
        if (!"FrontMane".equals(bone) && !"Ears".equals(bone)) return false;
        ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof ArmorItem armor) || armor.getSlotType() != EquipmentSlot.HEAD) return false;
        return armor.getMaterial() == ArmorMaterials.TURTLE || "FrontMane".equals(bone) && hasPlume(armor);
    }

    static boolean hasPlume(ArmorItem armor) {
        if (armor.getSlotType() != EquipmentSlot.HEAD) return false;
        var material = armor.getMaterial();
        return material != null && material != ArmorMaterials.LEATHER && material != ArmorMaterials.TURTLE;
    }

    static String piece(String bone, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Head".equals(bone) ? "helmet" : null;
            case CHEST -> "Body".equals(bone) ? "chest" : "Neck".equals(bone) ? "neck" : null;
            case LEGS -> "Body".equals(bone) ? "legs" : null;
            case FEET -> switch (bone) {
                case "LFrontHoof" -> "lfront";
                case "RFrontHoof" -> "rfront";
                case "LHindHoof" -> "lhind";
                case "RHindHoof" -> "rhind";
                default -> null;
            };
            default -> null;
        };
    }

    static String material(ArmorItem armor) {
        if (armor.getMaterial() == ArmorMaterials.LEATHER) return "leather";
        if (armor.getMaterial() == ArmorMaterials.CHAIN) return "chainmail";
        if (armor.getMaterial() == ArmorMaterials.IRON) return "iron";
        if (armor.getMaterial() == ArmorMaterials.GOLD) return "gold";
        if (armor.getMaterial() == ArmorMaterials.DIAMOND) return "diamond";
        if (armor.getMaterial() == ArmorMaterials.NETHERITE) return "netherite";
        if (armor.getMaterial() == ArmorMaterials.TURTLE) return "turtle";
        return null;
    }

    static Identifier texture(String material, EquipmentSlot slot) {
        if ("turtle".equals(material)) return slot == EquipmentSlot.HEAD ? TURTLE_TEXTURE : null;
        return switch (slot) {
            case HEAD, CHEST, LEGS, FEET -> id("textures/armor/" + material + ".png");
            default -> null;
        };
    }

    public static void renderAtBone(PonyRenderer renderer, MatrixStack matrices, GeoBone bone,
            VertexConsumerProvider buffers, AbstractClientPlayerEntity player, int light, int overlay,
            BodyFlightAura.Capture aura) {
        if (player == null || player.isSpectator() || bone.isHidden()) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String piece = piece(bone.getName(), slot);
            if (piece == null) continue;
            ItemStack stack = player.getEquippedStack(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlotType() != slot) continue;
            String material = material(armor);
            boolean automatic = material == null;
            Identifier texture = automatic ? PonyArmorAutoColors.get(stack, armor, player) : texture(material, slot);
            if (texture == null) continue;
            Identifier plumeTexture = texture;
            boolean turtle = "turtle".equals(material);
            var model = GeckoLibCache.getBakedModels().get(turtle ? TURTLE : "leather".equals(material) ? LEATHER : METAL);
            if (model == null) continue;
            GeoBone geometry = model.getBone(piece).orElse(null);
            if (geometry == null) continue;
            int color = 0xFFFFFF;
            if (!automatic && armor instanceof DyeableItem dyeable && dyeable.hasColor(stack)) {
                Identifier neutral = PonyArmorTextures.neutral(texture);
                if (neutral != null) {
                    texture = neutral;
                    color = dyeable.getColor(stack);
                }
            }
            matrices.push();
            try {
                RenderUtils.prepMatrixForBone(matrices, bone);
                if (turtle) fitTurtle(matrices);
                renderPart(renderer, matrices, geometry, buffers, texture, color, stack.hasGlint(), light, overlay, aura);
                if (hasPlume(armor)) {
                    GeoBone plume = model.getBone("plume").orElse(null);
                    if (plume != null) {
                        // 红盔缨已烘入头盔同一图集，不读取皮甲染色。
                        renderPart(renderer, matrices, plume, buffers, plumeTexture,
                                0xFFFFFF, stack.hasGlint(), light, overlay, aura);
                    }
                }
            } finally { matrices.pop(); }
        }
    }

    private static void renderPart(PonyRenderer renderer, MatrixStack matrices, GeoBone geometry,
            VertexConsumerProvider buffers, Identifier texture, int color, boolean glint, int light, int overlay,
            BodyFlightAura.Capture aura) {
        RenderLayer layer = RenderLayer.getArmorCutoutNoCull(texture);
        VertexConsumer consumer = ItemRenderer.getArmorGlintConsumer(buffers, layer, false, glint);
        // 合并后的 consumer 每个顶点只采样一次包身光剪影。
        if (aura != null) consumer = aura.wrap(consumer, texture);
        float red = (color >> 16 & 255) / 255f, green = (color >> 8 & 255) / 255f, blue = (color & 255) / 255f;
        for (var cube : geometry.getCubes()) {
            matrices.push();
            try { renderer.renderCube(matrices, cube, consumer, light, overlay, red, green, blue, 1); }
            finally { matrices.pop(); }
        }
    }

    static void fitTurtle(MatrixStack matrices) {
        // 8×8×8 的原版头盔盒映射到当前 8×9×9 头部，保留旧 UV 与膨胀外壳。
        // 上移半个模型像素，保留头盔压低的边缘。
        matrices.translate(0, 26.0 / 16, -5.0 / 16);
        matrices.scale(1, 9f / 8, 9f / 8);
        matrices.translate(0, -28.0 / 16, 0);
    }

    private static Identifier id(String path) { return new Identifier("magicaland", path); }
}
