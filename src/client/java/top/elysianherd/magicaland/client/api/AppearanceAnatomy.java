package top.elysianherd.magicaland.client.api;

import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.network.NetworkHandler;

/** Display-only copies. Callers must never store the result as an applied model or editor draft. */
public final class AppearanceAnatomy {
    private AppearanceAnatomy() {}

    public static ModelConfig apply(UUID player, ModelConfig source) {
        if (player == null || source == null) return source;
        var anatomy = AppearanceOverrideState.anatomy(player);
        if (anatomy == null || (source.showHorn == anatomy.hasHorn() && source.showWings == anatomy.hasWings()))
            return source;
        ModelConfig display = source.copyForDisplay();
        display.showHorn = anatomy.hasHorn();
        display.showWings = anatomy.hasWings();
        return display;
    }

    public static ModelConfig forPlayer(LivingEntity player) {
        if (player == null) return null;
        var client = MinecraftClient.getInstance();
        boolean local = client.player != null && player.getUuid().equals(client.player.getUuid());
        ModelConfig source = local ? ModelManager.getAppliedModel()
                : NetworkHandler.serverHasMod ? ClientNetworkHandler.remoteModels.get(player.getUuid()) : null;
        return apply(player.getUuid(), source);
    }
}
