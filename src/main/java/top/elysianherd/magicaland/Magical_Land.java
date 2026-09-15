package top.elysianherd.magicaland;

import net.fabricmc.api.ModInitializer;
import top.elysianherd.magicaland.network.NetworkHandler;

public class Magical_Land implements ModInitializer {

    @Override
    public void onInitialize() {
        NetworkHandler.registerServer();
    }
}
