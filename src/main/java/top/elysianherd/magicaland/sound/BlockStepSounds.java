package top.elysianherd.magicaland.sound;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;

public final class BlockStepSounds {
    private static Set<SoundEvent> sounds;
    private BlockStepSounds() {}

    public static boolean contains(SoundEvent sound) {
        if (sounds == null) {
            var found = Collections.newSetFromMap(new IdentityHashMap<SoundEvent, Boolean>());
            for (var block : Registries.BLOCK) for (var state : block.getStateManager().getStates())
                found.add(state.getSoundGroup().getStepSound());
            sounds = Collections.unmodifiableSet(found);
        }
        return sounds.contains(sound);
    }
}
