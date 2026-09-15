package top.elysianherd.magicaland.api.client;

/** Immutable applied colors and effective anatomy; not server gameplay authority. Colors are RGB. */
public record AppearanceSnapshot(boolean modelReplacementEnabled, boolean hasHorn, boolean hasWings,
                                 int magicColor) {}
