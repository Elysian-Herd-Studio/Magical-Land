package top.elysianherd.magicaland.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.elysianherd.magicaland.client.render.PonyGuiGaze;

@Mixin({InventoryScreen.class, CreativeInventoryScreen.class})
public abstract class InventoryScreenMixin extends Screen {
    protected InventoryScreenMixin(Text title) { super(title); }

    @WrapOperation(
            method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIFFLnet/minecraft/entity/LivingEntity;)V"),
            require = 1, allow = 1)
    private void magicaland$inventoryGaze(DrawContext context, int x, int y, int scale,
            float offsetX, float offsetY, LivingEntity entity, Operation<Void> original,
            DrawContext screenContext, float delta, int mouseX, int mouseY) {
        try (var scope = PonyGuiGaze.begin(this, entity, mouseX, mouseY, width, height)) {
            original.call(context, x, y, scale, offsetX, offsetY, entity);
        }
    }
}
