package top.elysianherd.magicaland.client.render;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import top.elysianherd.magicaland.api.client.AppearanceOverrides;
import top.elysianherd.magicaland.client.api.AppearanceAnatomy;
import top.elysianherd.magicaland.client.api.FirstPersonItemView;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.network.NetworkHandler;

public final class MagicEquip {
    private static final Map<UUID, Entry> PLAYERS = new HashMap<>();
    private static Object world;
    private static boolean initialized;

    private MagicEquip() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.world) { PLAYERS.clear(); world = client.world; }
            if (client.world == null) return;
            if (client.isPaused()) return;
            var present = new HashSet<UUID>();
            for (var player : client.world.getPlayers()) {
                if (!enabled(player)) continue;
                present.add(player.getUuid());
                entry(player, 0);
            }
            PLAYERS.keySet().retainAll(present);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PLAYERS.clear(); world = null;
        });
    }

    public static boolean enabled(LivingEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(player instanceof AbstractClientPlayerEntity) || player.getWorld() != client.world
                || !Config.getInstance().replacePlayerModel || !player.isAlive() || player.isRemoved()
                || player.isInvisible() || player.isSpectator() || player.isSleeping() || player.hasVehicle()) return false;
        ModelConfig model = player == client.player ? ModelManager.getAppliedModel()
                : NetworkHandler.serverHasMod ? ClientNetworkHandler.remoteModels.get(player.getUuid()) : null;
        model = AppearanceAnatomy.apply(player.getUuid(), model);
        return model != null && model.showHorn;
    }

    private static Entry entry(LivingEntity player, float delta) {
        if (!enabled(player)) { PLAYERS.remove(player.getUuid()); return null; }
        if (world != player.getWorld()) { PLAYERS.clear(); world = player.getWorld(); }
        Entry result = PLAYERS.get(player.getUuid());
        if (result == null || result.entity != player || player.age < result.clock.age()) {
            if (PLAYERS.size() >= 512) PLAYERS.clear();
            result = new Entry(player);
            PLAYERS.put(player.getUuid(), result);
        }
        result.observe(result.clock.read(player.age, delta));
        return result;
    }

    public static float hornProgress(LivingEntity player, float delta) {
        Entry state = entry(player, delta);
        return state == null ? 0 : state.horn.progress(state.clock.seconds());
    }

    public static float progress(LivingEntity player, boolean main, float delta) {
        var view = FirstPersonItemView.forOwner(player);
        if (view != null) return view.context == null ? 1 : main ? view.context.equipProgress() : 0;
        Entry state = entry(player, delta);
        return state == null ? 1 : state.hand(main).motion.progress(state.clock.seconds());
    }

    public static boolean transitioning(LivingEntity player, boolean main, float delta) {
        var view = FirstPersonItemView.forOwner(player);
        if (view != null) return view.context != null && main && !view.stack.isEmpty()
                && view.context.equipProgress() < 1;
        Entry state = entry(player, delta);
        if (state == null) return false;
        HandState hand = state.hand(main);
        float progress = hand.motion.progress(state.clock.seconds());
        return hand.held ? progress < 1 : progress > 0;
    }

    public static boolean instantSwap(LivingEntity player, float delta) {
        var view = FirstPersonItemView.forOwner(player);
        if (view != null && view.context != null) return false;
        Entry state = entry(player, delta);
        return state != null && state.clock.seconds() < state.instantUntil;
    }

    public static ItemStack visualStack(LivingEntity player, boolean main, float delta) {
        var view= FirstPersonItemView.forOwner(player);
        if (view!=null) return main?view.stack:ItemStack.EMPTY;
        if (main && AppearanceOverrides.mainHandVisibility(player.getUuid())
                == AppearanceOverrides.Visibility.HIDDEN) return ItemStack.EMPTY;
        ItemStack actual = main ? player.getMainHandStack() : player.getOffHandStack();
        Entry state = entry(player, delta);
        if (state == null || !actual.isEmpty()) return actual;
        HandState hand = state.hand(main);
        return hand.motion.progress(state.clock.seconds()) > 0 ? hand.retained : ItemStack.EMPTY;
    }

    public static float scale(LivingEntity player, boolean main, float delta) {
        return MagicEquipMotion.scale(progress(player, main, delta));
    }

    public static boolean overrideFirstPerson(LivingEntity player, boolean main, float delta,
            ItemStack vanillaStack, float vanillaEquip) {
        var view = FirstPersonItemView.forOwner(player);
        if (view != null && view.context != null) return false;
        Entry state = entry(player, delta);
        if (state == null) return false;
        HandState hand = state.hand(main);
        ItemStack actual = main ? player.getMainHandStack() : player.getOffHandStack();
        float progress = hand.motion.progress(state.clock.seconds());
        boolean transitioning = hand.held ? progress < 1 : progress > 0;
        return state.clock.seconds() < state.instantUntil || hand.handoff.owns(transitioning,
                ItemStack.areEqual(actual, vanillaStack), hand.held, vanillaEquip);
    }

    public static boolean firstPersonHandoff(LivingEntity player, boolean main, float delta) {
        var view = FirstPersonItemView.forOwner(player);
        if (view != null && view.context != null) return false;
        Entry state = entry(player, delta);
        return state != null && state.hand(main).handoff.active();
    }

    public static void firstPersonPath(LivingEntity player, boolean main, boolean left, MatrixStack matrices, float delta) {
        var view = FirstPersonItemView.forOwner(player);
        if (view != null && view.context != null) return;
        if (!enabled(player)) return;
        var offset = MagicEquipMotion.offset(progress(player, main, delta), left, true);
        matrices.translate(offset.x(), offset.y(), offset.z());
    }

    private static final class Entry {
        final LivingEntity entity;
        final HandState main = new HandState(), off = new HandState();
        final MagicEquipMotion horn = new MagicEquipMotion();
        final MagicEquipMotion.Clock clock = new MagicEquipMotion.Clock();
        int slot = -1;
        double instantUntil = Double.NEGATIVE_INFINITY;
        Entry(LivingEntity entity) { this.entity = entity; }
        HandState hand(boolean isMain) { return isMain ? main : off; }
        void observe(double seconds) {
            ItemStack actualMain = entity.getMainHandStack(), actualOff = entity.getOffHandStack();
            boolean exchanged = MagicEquipMotion.exchanged(
                    ItemStack.areEqual(actualMain, main.held ? main.retained : ItemStack.EMPTY),
                    ItemStack.areEqual(actualOff, off.held ? off.retained : ItemStack.EMPTY),
                    ItemStack.areEqual(actualMain, off.held ? off.retained : ItemStack.EMPTY),
                    ItemStack.areEqual(actualOff, main.held ? main.retained : ItemStack.EMPTY));
            int selected = entity == MinecraftClient.getInstance().player
                    ? MinecraftClient.getInstance().player.getInventory().selectedSlot : -1;
            boolean swap = slot >= 0 && selected != slot && main.held && !entity.getMainHandStack().isEmpty();
            boolean changed = main.held != !actualMain.isEmpty() || off.held != !actualOff.isEmpty()
                    || (slot >= 0 && selected != slot)
                    || (main.held && !actualMain.isEmpty() && main.retained.getItem() != actualMain.getItem())
                    || (off.held && !actualOff.isEmpty() && off.retained.getItem() != actualOff.getItem());
            main.observe(actualMain, seconds);
            if (swap) main.settle(seconds);
            slot = selected;
            off.observe(actualOff, seconds);
            horn.observe(main.held || off.held
                    || AppearanceOverrides.magicActive(entity.getUuid()), seconds);
            if (exchanged) {
                main.settle(seconds);
                off.settle(seconds);
                horn.settle(seconds);
                instantUntil = seconds + .35;
                for (boolean first : new boolean[] {false, true}) {
                    ItemLevitation.forget(entity, true, first);
                    ItemLevitation.forget(entity, false, first);
                }
            } else if (changed) instantUntil = Double.NEGATIVE_INFINITY;
        }
    }

    private static final class HandState {
        final MagicEquipMotion motion = new MagicEquipMotion();
        final MagicEquipMotion.Handoff handoff = new MagicEquipMotion.Handoff();
        ItemStack retained = ItemStack.EMPTY;
        boolean held, observed;
        void settle(double seconds) {
            motion.settle(seconds);
            handoff.release();
            if (!held) retained = ItemStack.EMPTY;
        }
        void observe(ItemStack stack, double seconds) {
            boolean present = !stack.isEmpty();
            boolean swap = held && present && retained.getItem() != stack.getItem();
            if (observed && held != present) handoff.begin();
            motion.observe(present, seconds);
            if (swap) { motion.settle(seconds); handoff.release(); }
            held = present;
            observed = true;
            if (present && !ItemStack.areEqual(retained, stack)) retained = stack.copy();
            else if (!present && motion.progress(seconds) == 0) retained = ItemStack.EMPTY;
        }
    }
}
