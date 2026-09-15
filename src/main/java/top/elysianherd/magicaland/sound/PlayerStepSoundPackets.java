package top.elysianherd.magicaland.sound;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;

public final class PlayerStepSoundPackets {
    private PlayerStepSoundPackets() {}

    public static boolean send(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        if (!PlayerStepSoundScope.matches(player, sound)) return false;
        var world = player.getServerWorld();
        var entry = Registries.SOUND_EVENT.getEntry(sound);
        var category = player.getSoundCategory();
        long seed = world.random.nextLong();
        var entityPacket = new PlaySoundFromEntityS2CPacket(entry, category, player, volume, pitch, seed);
        var positionPacket = new PlaySoundS2CPacket(entry, category, player.getX(), player.getY(), player.getZ(), volume, pitch, seed);
        var tracking = PlayerLookup.tracking(player);
        double range = sound.getDistanceToTravel(volume), rangeSquared = range * range;
        for (var observer : PlayerLookup.around(world, player.getPos(), range)) {
            var delivery = HoofStepProtocol.delivery(observer == player, observer.getWorld() == world,
                    observer.squaredDistanceTo(player) < rangeSquared, tracking.contains(observer) && !player.isSilent());
            // 尚未跟踪实体时保留坐标声，原版客户端也不会丢失脚步。
            if (delivery == HoofStepProtocol.Delivery.ENTITY) observer.networkHandler.sendPacket(entityPacket);
            else if (delivery == HoofStepProtocol.Delivery.POSITION) observer.networkHandler.sendPacket(positionPacket);
        }
        return true;
    }
}
