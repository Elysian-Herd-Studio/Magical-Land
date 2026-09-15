package top.elysianherd.magicaland.mixin.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.client.sound.PonyHoofStepPackets;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class PonyHoofSoundNetworkMixin {
    @Inject(method = "onPlaySoundFromEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void magicaland$identifiedHoofStep(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) {
        if (PonyHoofStepPackets.intercept((ClientPlayNetworkHandler)(Object)this, packet)) ci.cancel();
    }
}
