package top.elysianherd.magicaland.api.client;

import java.util.Optional;
import java.util.UUID;
import top.elysianherd.magicaland.client.api.AppearanceAccess;

/** Read on the client thread. Queries never expose configuration or network caches. */
public final class Appearances {
    public static final int DEFAULT_MAGIC_COLOR = 0xAA00FF;

    private Appearances() {}

    public static Optional<AppearanceSnapshot> find(UUID player) {
        return AppearanceAccess.find(player);
    }

    public static int magicColor(UUID player) {
        return find(player).map(AppearanceSnapshot::magicColor).orElse(DEFAULT_MAGIC_COLOR);
    }
}
