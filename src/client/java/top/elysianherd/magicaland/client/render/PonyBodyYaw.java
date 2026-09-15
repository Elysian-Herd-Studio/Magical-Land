package top.elysianherd.magicaland.client.render;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/** 原版 LivingEntityRenderer 的骑乘朝向分支，只计算绘制角度，不改实体状态。 */
public final class PonyBodyYaw {
    private PonyBodyYaw() {}

    public static boolean hasLivingMount(LivingEntity rider) {
        return rider.hasVehicle() && rider.getVehicle() instanceof LivingEntity;
    }

    public static float sample(LivingEntity rider, float partialTick) {
        float own = MathHelper.lerpAngleDegrees(partialTick, rider.prevBodyYaw, rider.bodyYaw);
        if (!hasLivingMount(rider)) return own;
        LivingEntity mount = (LivingEntity) rider.getVehicle();
        float body = MathHelper.lerpAngleDegrees(partialTick, mount.prevBodyYaw, mount.bodyYaw);
        float head = MathHelper.lerpAngleDegrees(partialTick, rider.prevHeadYaw, rider.headYaw);
        return Float.isFinite(body) && Float.isFinite(head) ? mounted(body, head) : own;
    }

    static float mounted(float mountBodyYaw, float riderHeadYaw) {
        float relative = MathHelper.clamp(MathHelper.wrapDegrees(riderHeadYaw - mountBodyYaw), -85, 85);
        float body = riderHeadYaw - relative;
        if (relative * relative > 2500) body += relative * .2f;
        return body;
    }
}
