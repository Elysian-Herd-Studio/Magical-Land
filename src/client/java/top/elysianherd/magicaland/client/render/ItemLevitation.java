package top.elysianherd.magicaland.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.elysianherd.magicaland.api.client.ItemVisualContext;
import top.elysianherd.magicaland.client.api.FirstPersonItemView;

public final class ItemLevitation {
    private static final LevitationMotion.Store STATES = new LevitationMotion.Store();
    private static final Map<LevitationMotion.Key, ItemStamp> ITEMS = new HashMap<>();
    private static final LevitationMotion.Store VISUAL_STATES = new LevitationMotion.Store();
    private static final Map<LevitationMotion.Key, ItemStamp> VISUAL_ITEMS = new HashMap<>();
    private static boolean initialized;
    private static Object trackedWorld;
    private static boolean trackedFirstPerson;
    private static Matrix4f visualWorldFrame;
    private static Vec3d visualWorldOrigin;

    private ItemLevitation() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        MagicEquip.init();
        WorldRenderEvents.START.register(context -> {
            boolean first = MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
            if (trackedWorld != context.world() || trackedFirstPerson != first) clear();
            trackedWorld = context.world();
            trackedFirstPerson = first;
            visualWorldFrame = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
            visualWorldOrigin = context.camera().getPos();
            STATES.beginFrame(context.world(), first);
            VISUAL_STATES.beginFrame(context.world(), first);
            ITEMS.values().removeIf(item -> STATES.frame() - item.lastSeen > 120);
            VISUAL_ITEMS.values().removeIf(item -> VISUAL_STATES.frame() - item.lastSeen > 120);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void clear() {
        STATES.clear();
        ITEMS.clear();
        VISUAL_STATES.clear();
        VISUAL_ITEMS.clear();
        trackedWorld = null;
        visualWorldFrame = null;
        visualWorldOrigin = null;
    }

    public static void forget(LivingEntity entity, boolean main, boolean first) {
        var view = first ? FirstPersonItemView.forOwner(entity) : null;
        forget(view == null ? entity : view.camera, main, first, view != null);
    }

    private static void forget(Entity source, boolean main, boolean first, boolean visual) {
        LevitationMotion.Key key = new LevitationMotion.Key(source.getUuid(), main, first);
        (visual ? VISUAL_STATES : STATES).remove(key);
        (visual ? VISUAL_ITEMS : ITEMS).remove(key);
    }

    public static LevitationTrail applyWorld(LivingEntity entity, ItemStack stack, boolean main, boolean left,
            MatrixStack matrices, Matrix4f entityFrame, Matrix4f anchorFrame, float delta) {
        if (!HornAuraPass.isWorld() || inactive(entity, stack)) {
            forget(entity, main, false);
            return LevitationTrail.EMPTY;
        }
        Vec3d player = entity.getLerpedPos(delta);
        Matrix4f inverseFrame = new Matrix4f(entityFrame).invert();
        Vector3f anchor = inverseFrame.transformPosition(anchorFrame.transformPosition(new Vector3f(), new Vector3f()));
        LevitationMotion.Point target = point(player).add(point(anchor));
        Vector3f entrance = vector(MagicEquipMotion.springTarget(MagicEquip.progress(entity, main, delta), left, false));
        anchorFrame.transformDirection(entrance);
        inverseFrame.transformDirection(entrance);
        target = target.add(point(entrance));
        Matrix3f worldToRender = new Matrix3f(entityFrame);
        float yaw = MathHelper.lerpAngleDegrees(delta, entity.prevBodyYaw, entity.bodyYaw);
        return apply(entity, stack, main, left, false, matrices, target, point(player), worldToRender,
                new Matrix4f(entityFrame), point(player), yaw, delta);
    }

    public static LevitationTrail applyFirstPerson(LivingEntity entity, ItemStack stack, boolean main, boolean left,
            MatrixStack matrices, float delta) {
        var view = FirstPersonItemView.forOwner(entity);
        var context = view == null ? null : view.context;
        var source = view == null ? entity : view.camera;
        if (context == null ? inactive(entity, stack) : inactive(source, stack)) {
            forget(entity, main, true);
            return LevitationTrail.EMPTY;
        }
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d cameraPosition = camera.getPos();
        Quaternionf rotation = new Quaternionf(camera.getRotation());
        Matrix3f worldToRender = new Matrix3f().rotation(new Quaternionf(rotation).conjugate());
        Vector3f anchor = new Vector3f(left ? -.71f : .71f, -.32f, -1.12f);
        if (context != null) {
            Vector3f shift = new Vector3f(left ? .19f : -.19f, .08f, -.28f);
            anchor.add(shift);
            new Matrix3f(matrices.peek().getPositionMatrix()).invert().transform(shift);
            if (shift.isFinite()) matrices.translate(shift.x, shift.y, shift.z);
        }
        anchor.rotate(rotation);
        LevitationMotion.Point target = point(cameraPosition).add(point(anchor));
        if (context == null)
            target = target.add(point(vector(MagicEquipMotion.springTarget(MagicEquip.progress(entity, main, delta), left, true)).rotate(rotation)));
        return applyMotion(source, context == null ? entity.age : source.age, stack,
                main, left, true, view != null, matrices, target, point(source.getLerpedPos(delta)),
                worldToRender, new Matrix4f().set(worldToRender), point(cameraPosition),
                source.getYaw(delta), delta, MagicEquip.progress(entity, main, delta),
                context == null ? entity.isUsingItem() : context.usingItem() || context.swingProgress() > 0,
                context == null ? entity.isSprinting() : context.sprinting());
    }

    public static LevitationTrail applyVisualWorld(ItemVisualContext context, ItemStack stack, boolean left,
            MatrixStack matrices, float delta) {
        Entity source = context.source();
        if (!HornAuraPass.isWorld() || visualWorldFrame == null || visualWorldOrigin == null || inactive(source, stack)) {
            forget(source, !left, false, true);
            return LevitationTrail.EMPTY;
        }
        Matrix4f worldFrame = new Matrix4f(visualWorldFrame);
        Matrix3f worldToRender = new Matrix3f(worldFrame);
        Vector3f anchor = LevitationRenderSpace.anchor(worldFrame, matrices.peek().getPositionMatrix());
        LevitationMotion.Point origin = point(visualWorldOrigin);
        return applyMotion(source, source.age, stack, !left, left, false, true, matrices, origin.add(point(anchor)),
                point(source.getLerpedPos(delta)), worldToRender, worldFrame, origin,
                MathHelper.lerpAngleDegrees(delta, source.prevYaw, source.getYaw()), delta, context.equipProgress(),
                context.usingItem() || context.swingProgress() > 0, context.sprinting());
    }

    private static LevitationTrail apply(LivingEntity entity, ItemStack stack, boolean main, boolean left,
            boolean first, MatrixStack matrices, LevitationMotion.Point target, LevitationMotion.Point player,
            Matrix3f worldToRender, Matrix4f worldFrame, LevitationMotion.Point origin, float yaw, float delta) {
        return applyMotion(entity, entity.age, stack, main, left, first, false, matrices, target, player, worldToRender,
                worldFrame, origin, yaw, delta, MagicEquip.progress(entity, main, delta),
                entity.isUsingItem(), entity.isSprinting());
    }

    private static LevitationTrail applyMotion(Entity source, int age, ItemStack stack, boolean main, boolean left,
            boolean first, boolean visual, MatrixStack matrices, LevitationMotion.Point target,
            LevitationMotion.Point player, Matrix3f worldToRender, Matrix4f worldFrame,
            LevitationMotion.Point origin, float yaw, float delta, float equip, boolean using, boolean sprinting) {
        double time = (age + (double) delta) / 20;
        LevitationMotion.Key key = new LevitationMotion.Key(source.getUuid(), main, first);
        LevitationMotion.Store states = visual ? VISUAL_STATES : STATES;
        LevitationMotion.Profile profile = first ? LevitationMotion.FIRST_PERSON : LevitationMotion.WORLD;
        LevitationMotion.Entry entry = states.acquire(key,
                identity(states, visual ? VISUAL_ITEMS : ITEMS, key, stack, left, time), profile);
        states.sample(entry, target, player, yaw, time, using);
        if (entry.pose == null || !target.finite()) return LevitationTrail.EMPTY;
        Matrix3f renderToLocal = new Matrix3f(matrices.peek().getPositionMatrix()).invert();
        double flight = MagicEquipMotion.flight(equip);
        Vector3f displacement = worldToRender.transform(vector(entry.pose.offset().multiply(flight)), new Vector3f());
        renderToLocal.transform(displacement);
        Vector3f yawAxis = worldToRender.transform(new Vector3f(0, 1, 0), new Vector3f());
        renderToLocal.transform(yawAxis);
        if (!displacement.isFinite() || !yawAxis.isFinite() || yawAxis.lengthSquared() < 1e-10f)
            return LevitationTrail.EMPTY;
        matrices.translate(displacement.x, displacement.y, displacement.z);
        matrices.multiply(new Quaternionf().rotationAxis((float) Math.toRadians(-entry.pose.yaw() * flight), yawAxis.normalize()));
        float scale = MagicEquipMotion.scale(equip);
        // 调用方在零尺寸时不绘制；此处只缩物品局部，不缩飞行路径。
        if (scale > 0 && scale < 1) matrices.scale(scale, scale, scale);
        Matrix4f inverseWorld = new Matrix4f(worldFrame).invert();
        // ItemRenderer 的显示变换也会移动几何中心，等 capture 完成再记录尾迹。
        return LevitationTrail.deferred(center -> {
            Vector3f actualRelative = inverseWorld.transformPosition(new Vector3f(center));
            LevitationMotion.Point actual = origin.add(point(actualRelative));
            var trail = states.recordTrail(entry, actual, time, sprinting, using || flight < 1);
            return LevitationTrail.create(trail, worldFrame, origin, entry.motion.trailWidth());
        });
    }

    private static boolean inactive(LivingEntity entity, ItemStack stack) {
        return stack.isEmpty() || !entity.isAlive() || entity.isInvisible() || entity.isSpectator()
                || entity.isSleeping() || entity.hasVehicle() || MinecraftClient.getInstance().world != entity.getWorld();
    }

    private static boolean inactive(Entity source, ItemStack stack) {
        return stack.isEmpty() || source.isRemoved() || MinecraftClient.getInstance().world != source.getWorld();
    }

    private static String identity(LevitationMotion.Store states, Map<LevitationMotion.Key, ItemStamp> items,
            LevitationMotion.Key key, ItemStack stack, boolean left, double time) {
        ItemStamp stamp = items.computeIfAbsent(key, ignored -> new ItemStamp());
        stamp.lastSeen = states.frame();
        // 换槽立即比较；同一 stack 的 NBT 仅低频检查，平常不复制/序列化。
        if (stamp.stack != stack || stamp.left != left || time < stamp.checked || time - stamp.checked >= .25) {
            if (stamp.item != stack.getItem() || stamp.left != left || !Objects.equals(stamp.nbt, stack.getNbt())) {
                stamp.item = stack.getItem();
                stamp.left = left;
                stamp.nbt = stack.getNbt() == null ? null : stack.getNbt().copy();
                stamp.signature = Long.toString(++stamp.generation);
            }
            stamp.stack = stack;
            stamp.checked = time;
        }
        if (items.size() > 512) { items.clear(); states.clear(); }
        return stamp.signature;
    }

    private static final class ItemStamp {
        ItemStack stack;
        Item item;
        NbtCompound nbt;
        boolean left;
        double checked;
        long generation, lastSeen;
        String signature = "0";
    }

    private static LevitationMotion.Point point(Vec3d v) { return new LevitationMotion.Point(v.x, v.y, v.z); }
    private static LevitationMotion.Point point(Vector3f v) { return new LevitationMotion.Point(v.x, v.y, v.z); }
    private static Vector3f vector(LevitationMotion.Point p) { return new Vector3f((float) p.x(), (float) p.y(), (float) p.z()); }
}
