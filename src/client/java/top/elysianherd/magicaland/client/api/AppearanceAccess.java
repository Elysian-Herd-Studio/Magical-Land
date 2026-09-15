package top.elysianherd.magicaland.client.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import top.elysianherd.magicaland.api.client.AppearanceSnapshot;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.client.render.GlowingItem;
import top.elysianherd.magicaland.network.NetworkHandler;

public final class AppearanceAccess {
    private AppearanceAccess() {}

    public static Optional<AppearanceSnapshot> find(UUID player) {
        Objects.requireNonNull(player, "player");
        var client = MinecraftClient.getInstance();
        boolean local = client.player != null && player.equals(client.player.getUuid());
        var model = local ? ModelManager.getAppliedModel() : ClientNetworkHandler.remoteModels.get(player);
        if (model == null) return Optional.empty();
        model = AppearanceAnatomy.apply(player, model);
        return Optional.of(new AppearanceSnapshot(Config.getInstance().replacePlayerModel
                && (local || NetworkHandler.serverHasMod), model.showHorn, model.showWings,
                GlowingItem.getGlowColor(model)));
    }
}
