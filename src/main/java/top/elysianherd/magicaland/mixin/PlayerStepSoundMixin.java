package top.elysianherd.magicaland.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.elysianherd.magicaland.sound.PlayerStepSoundPackets;
import top.elysianherd.magicaland.sound.PlayerStepSoundScope;

@Mixin(PlayerEntity.class)
public abstract class PlayerStepSoundMixin {
    @WrapMethod(method = "playStepSound(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V")
    private void magicaland$stepScope(BlockPos pos, BlockState state, Operation<Void> original) {
        var player = (PlayerEntity)(Object)this;
        if (player.isTouchingWater()) { original.call(pos, state); return; }
        var covering = player.getWorld().getBlockState(pos.up()).getSoundGroup().getStepSound();
        try (var scope = PlayerStepSoundScope.enter(player, state.getSoundGroup().getStepSound(), covering)) {
            original.call(pos, state);
        }
    }

    @Inject(method = "playSound(Lnet/minecraft/sound/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void magicaland$identifiedStep(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity player && PlayerStepSoundPackets.send(player, sound, volume, pitch)) ci.cancel();
    }
}
