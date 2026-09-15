package top.elysianherd.magicaland.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** 只观察原版蓄力和新投掷物，不改变使用、弹道或同步。 */
public final class PonyTridentVisuals {
    private static final Map<UUID, Entry> states = new HashMap<>();
    private static final ArrayList<Candidate> pending = new ArrayList<>();
    private static final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
    private static ClientWorld world;
    private static long tick;
    private static boolean registered;

    private static final class Entry {
        final AbstractClientPlayerEntity player;
        final PonyTridentMotion motion = new PonyTridentMotion();
        PonyTridentMotion.Input input;
        long session, evidence, lastCharge;
        Hand hand = Hand.MAIN_HAND;
        boolean left, validSession, wasCharging;
        double charge, maxCharge;
        Entry(AbstractClientPlayerEntity player) { this.player = player; }
    }
    private static final class Candidate {
        final TridentEntity entity;
        final Entry entry;
        final long session, queued;
        final Vec3d eye;
        Vec3d velocity, position;
        boolean rejected;
        Candidate(TridentEntity entity, Entry entry, Vec3d eye) {
            this.entity = entity; this.entry = entry; this.eye = eye;
            session = entry.session; queued = tick;
        }
    }

    private PonyTridentVisuals() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientEntityEvents.ENTITY_LOAD.register(PonyTridentVisuals::loaded);
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            updateWorld(client.world);
            if (!client.isPaused()) pending.forEach(PonyTridentVisuals::capture);
        });
        ClientTickEvents.END_CLIENT_TICK.register(PonyTridentVisuals::tick);
    }

    static PonyTridentMotion.Pose sample(AbstractClientPlayerEntity player, double absoluteTick) {
        if (player == null || world == null || world != MinecraftClient.getInstance().world
                || player.getWorld() != world || !Config.getInstance().replacePlayerModel
                || !player.isAlive() || player.isRemoved()) return PonyTridentMotion.Pose.NONE;
        Entry entry = states.get(player.getUuid());
        return entry != null && entry.player == player ? entry.motion.sample(absoluteTick) : PonyTridentMotion.Pose.NONE;
    }

    private static void tick(MinecraftClient client) {
        updateWorld(client.world);
        if (world == null) return;
        if (client.isPaused()) {
            Entry local = client.player == null ? null : states.get(client.player.getUuid());
            if (local != null && client.currentScreen != null && local.validSession) {
                local.validSession = false;
                local.session++;
                local.motion.observe(new PonyTridentMotion.Input(local.player.age, true, false,
                        local.hand == Hand.OFF_HAND, local.left, local.wasCharging, local.charge,
                        false, true, local.evidence));
            }
            return;
        }
        tick++;
        states.entrySet().removeIf(entry -> entry.getValue().player.isRemoved()
                || world.getPlayerByUuid(entry.getKey()) != entry.getValue().player);
        if (!Config.getInstance().replacePlayerModel) { states.clear(); pending.clear(); return; }
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            Entry entry = states.get(player.getUuid());
            if (entry == null || entry.player != player) {
                entry = new Entry(player);
                states.put(player.getUuid(), entry);
            }
            observe(entry, client);
        }
        pending.removeIf(PonyTridentVisuals::verify);
        for (Entry entry : states.values()) {
            var in = entry.input;
            if (in != null) entry.motion.observe(new PonyTridentMotion.Input(in.tick(), in.eligible(),
                    in.mouthTrident(), in.offHand(), in.left(), in.charging(), in.chargeTicks(),
                    in.riptide(), in.cancelled(), entry.evidence));
        }
    }

    private static void observe(Entry entry, MinecraftClient client) {
        var player = entry.player;
        var config = PonyFlightVisuals.config(player);
        var frame = PonyHeldItems.frame(player, config, 0);
        boolean eligible = frame.player() != null && !player.isRemoved();
        Hand mouth = frame.mainGrip() == PonyHeldItems.Grip.MOUTH && frame.main().isOf(Items.TRIDENT) ? Hand.MAIN_HAND
                : frame.offGrip() == PonyHeldItems.Grip.MOUTH && frame.off().isOf(Items.TRIDENT) ? Hand.OFF_HAND : null;
        boolean charging = player.isUsingItem() && player.getActiveItem().isOf(Items.TRIDENT);
        double charge = charging ? player.getItemUseTime() : 0;
        boolean riptide = player.isUsingRiptide()
                || charging && EnchantmentHelper.getRiptide(player.getActiveItem()) > 0;
        boolean cancelled = player == client.player && client.currentScreen != null
                || charging && mouth != player.getActiveHand();
        if (charging && (!entry.wasCharging || charge + .5 < entry.charge)) {
            entry.session++;
            entry.hand = player.getActiveHand();
            entry.left = entry.hand == Hand.MAIN_HAND ? frame.mainLeft() : !frame.mainLeft();
            entry.validSession = eligible && !cancelled && !riptide && mouth == entry.hand;
            entry.maxCharge = 0;
        }
        if (entry.validSession) {
            var held = player.getStackInHand(entry.hand);
            boolean left = entry.hand == Hand.MAIN_HAND ? frame.mainLeft() : !frame.mainLeft();
            // 投出后原手变空是正常情况；换物、换手或改持握方式才取消。
            cancelled |= !held.isEmpty() && (!held.isOf(Items.TRIDENT) || mouth != entry.hand)
                    || left != entry.left
                    || charging && player.getActiveHand() != entry.hand;
            if (!eligible || cancelled || riptide) {
                entry.validSession = false;
                entry.session++;
            } else if (charging) {
                entry.maxCharge = Math.max(entry.maxCharge, charge);
                entry.lastCharge = tick;
            } else if (tick - entry.lastCharge > PonyTridentMotion.EVIDENCE_WINDOW) {
                entry.validSession = false;
            }
        }
        Hand hand = mouth == null ? entry.hand : mouth;
        boolean left = hand == Hand.MAIN_HAND ? frame.mainLeft() : !frame.mainLeft();
        entry.input = new PonyTridentMotion.Input(player.age, eligible, mouth != null && !cancelled,
                hand == Hand.OFF_HAND, left, charging, charge, riptide, cancelled, entry.evidence);
        entry.wasCharging = charging;
        entry.charge = charge;
    }

    private static void loaded(Entity entity, ClientWorld source) {
        if (!(entity instanceof TridentEntity trident) || source != MinecraftClient.getInstance().world) return;
        updateWorld(source);
        if (!seen.add(trident.getUuid())) return;
        if (seen.size() > 4096) seen.remove(seen.iterator().next());
        if (!(trident.getOwner() instanceof AbstractClientPlayerEntity owner)) return;
        Entry entry = states.get(owner.getUuid());
        if (entry == null || entry.player != owner || !entry.validSession
                || entry.maxCharge + 1 < PonyTridentMotion.MIN_CHARGE
                || tick - entry.lastCharge > PonyTridentMotion.EVIDENCE_WINDOW) return;
        Vec3d eye = owner.getEyePos();
        if (trident.squaredDistanceTo(eye) > 9) return;
        pending.add(new Candidate(trident, entry, eye));
    }

    private static void capture(Candidate candidate) {
        if (candidate.velocity != null || candidate.rejected) return;
        var trident = candidate.entity;
        if (trident.isRemoved() || trident.getWorld() != world || trident.isNoClip() || trident.returnTimer > 0) {
            candidate.rejected = true;
            return;
        }
        Vec3d velocity = trident.getVelocity();
        double speedSquared = velocity.lengthSquared();
        if (!Double.isFinite(speedSquared)) candidate.rejected = true;
        else if (speedSquared >= .25) {
            candidate.velocity = velocity;
            candidate.position = trident.getPos();
        }
    }

    private static boolean verify(Candidate candidate) {
        var trident = candidate.entity;
        var entry = candidate.entry;
        if (tick <= candidate.queued) return false;
        if (tick - candidate.queued > 3 || trident.getWorld() != world
                || states.get(entry.player.getUuid()) != entry || !entry.validSession
                || candidate.session != entry.session || trident.getOwner() != entry.player) return true;
        // START 在碰撞前保留初速度；END 再核对本轮换物/取消，贴墙投掷也不会漏掉。
        capture(candidate);
        if (candidate.rejected) return true;
        if (candidate.velocity == null) return false;
        Vec3d velocity = candidate.velocity;
        double speedSquared = velocity.lengthSquared();
        Vec3d outward = candidate.position.subtract(candidate.eye);
        if (outward.lengthSquared() > .25
                && outward.dotProduct(velocity) < -.25 * Math.sqrt(outward.lengthSquared() * speedSquared)) return true;
        entry.evidence++;
        entry.validSession = false;
        return true;
    }

    private static void updateWorld(ClientWorld next) {
        if (world != next) { clear(); world = next; }
    }
    private static void clear() {
        states.clear(); pending.clear(); seen.clear(); world = null; tick = 0;
    }
}
