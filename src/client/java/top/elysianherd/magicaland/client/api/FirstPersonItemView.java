package top.elysianherd.magicaland.client.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import top.elysianherd.magicaland.api.client.ItemVisualContext;

public final class FirstPersonItemView implements AutoCloseable {
    private static FirstPersonItemView current;
    private final FirstPersonItemView previous;
    private boolean closed;
    public final LivingEntity owner;
    public final Entity camera;
    public final ItemStack stack;
    public final ItemVisualContext context;
    private FirstPersonItemView(LivingEntity owner,Entity camera,ItemStack stack,ItemVisualContext context) {
        previous=current; this.owner=owner; this.camera=camera; this.stack=stack; this.context=context; current=this;
    }
    public static FirstPersonItemView open(LivingEntity owner,Entity camera,ItemStack stack) {
        return new FirstPersonItemView(owner,camera,stack,null);
    }
    public static FirstPersonItemView open(LivingEntity owner,ItemVisualContext context) {
        return new FirstPersonItemView(owner,context.source(),context.stack(),context);
    }
    public static FirstPersonItemView forOwner(LivingEntity owner) {
        return current!=null && current.owner==owner?current:null;
    }
    @Override public void close() {
        if (closed) return;
        if (current!=this) throw new IllegalStateException("First-person views must close in reverse order");
        closed=true;
        current=previous;
    }
}
