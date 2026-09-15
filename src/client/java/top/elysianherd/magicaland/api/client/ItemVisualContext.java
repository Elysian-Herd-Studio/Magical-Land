package top.elysianherd.magicaland.api.client;

import java.util.Objects;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/** One rendered item's visual state; never changes its source entity or gameplay state. */
public final class ItemVisualContext {
    private final Entity source;
    private final ItemStack stack;
    private final float equipProgress;
    private final float swingProgress;
    private final boolean usingItem;
    private final boolean sprinting;

    public ItemVisualContext(Entity source, ItemStack stack, float equipProgress, float swingProgress,
            boolean usingItem, boolean sprinting) {
        this.source = Objects.requireNonNull(source, "source");
        this.stack = Objects.requireNonNull(stack, "stack").copy();
        this.equipProgress = progress(equipProgress, "equipProgress");
        this.swingProgress = progress(swingProgress, "swingProgress");
        this.usingItem = usingItem;
        this.sprinting = sprinting;
    }

    /** The live pose/clock source and cache identity, independent of the appearance owner. */
    public Entity source() { return source; }
    public ItemStack stack() { return stack.copy(); }
    /** Zero is stowed; one is fully visible. Vanilla's lowered equip value has the opposite direction. */
    public float equipProgress() { return equipProgress; }
    public float swingProgress() { return swingProgress; }
    public boolean usingItem() { return usingItem; }
    public boolean sprinting() { return sprinting; }

    private static float progress(float value, String name) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        return value;
    }
}
