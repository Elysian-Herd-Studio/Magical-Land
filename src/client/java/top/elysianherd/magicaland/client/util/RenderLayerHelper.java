package top.elysianherd.magicaland.client.util;

import java.util.Optional;

import net.minecraft.util.Identifier;

public interface RenderLayerHelper {

    static Optional<Identifier> getTexture(net.minecraft.client.render.RenderLayer layer) {
        if (layer instanceof net.minecraft.client.render.RenderLayer.MultiPhase multiphase) {
            return multiphase.getPhases().texture.getId();
        }
        return Optional.empty();
    }
}
