package top.elysianherd.magicaland.mixin.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.client.sound.PonyHoofStepPackets;

@Mixin(ClientPlayerEntity.class)
public abstract class PonyHoofLocalSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sound/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void magicaland$localHoofStep(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        if (PonyHoofStepPackets.suppressLocal(this, sound)) ci.cancel();
    }
}
