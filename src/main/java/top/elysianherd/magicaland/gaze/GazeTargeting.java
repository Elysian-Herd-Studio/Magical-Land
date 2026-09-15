package top.elysianherd.magicaland.gaze;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.Set;

public final class GazeTargeting {
    private static final TagKey<EntityType<?>> HOSTILE = tag("gaze_hostile");
    private static final TagKey<EntityType<?>> NEUTRAL = tag("gaze_neutral");
    private static final TagKey<EntityType<?>> FRIENDLY = tag("gaze_friendly");
    private static final Set<EntityType<?>> BUILTIN_NEUTRAL = Set.of(EntityType.PIGLIN, EntityType.GOAT,
            EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.PANDA, EntityType.DOLPHIN);

    private GazeTargeting() {}

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("magicaland", name));
    }

    public static boolean eligible(PlayerEntity viewer, LivingEntity target) {
        return eligibleWithoutRay(viewer, target) && clearSight(viewer, target);
    }

    private static boolean eligibleWithoutRay(PlayerEntity viewer, LivingEntity target) {
        if (target == viewer || target.getWorld() != viewer.getWorld() || !target.isAlive()
                || target.isRemoved() || target.isSpectator() || target.isInvisible() || target.isInvisibleTo(viewer)
                || !(target instanceof MobEntity || target instanceof PlayerEntity)) return false;
        Vec3d delta = target.getPos().subtract(viewer.getPos());
        return GazePolicy.inView(delta.x, delta.y, delta.z, viewer.getHeadYaw());
    }

    private static boolean clearSight(PlayerEntity viewer, LivingEntity target) {
        Vec3d start = viewer.getEyePos();
        Vec3d end = target.getEyePos();
        if (viewer.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, viewer)).getType() != HitResult.Type.MISS) return false;
        for (var blocker : viewer.getWorld().getOtherEntities(viewer, new Box(start, end).expand(0.001),
                entity -> entity != target && entity instanceof LivingEntity && !entity.isSpectator()
                        && !entity.isRemoved() && !entity.isInvisible() && !entity.isInvisibleTo(viewer))) {
            if (blocker.getBoundingBox().contains(start) || blocker.getBoundingBox().raycast(start, end).isPresent()) return false;
        }
        return true;
    }

    public static int priority(PlayerEntity viewer, LivingEntity target) {
        boolean recent = viewer.getAttacker() == target
                && GazePolicy.recentAttack(viewer.age, viewer.getLastAttackedTime());
        boolean attacking = false;
        if (!viewer.getWorld().isClient && target instanceof MobEntity mob) {
            attacking = mob.getTarget() == viewer;
            var brainTarget = mob.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET);
            attacking |= brainTarget != null && brainTarget.orElse(null) == viewer;
        }
        if (!viewer.getWorld().isClient && target instanceof Angerable angry) attacking |= angry.shouldAngerAt(viewer);
        EntityType<?> type = target.getType();
        boolean neutral = type.isIn(NEUTRAL) || BUILTIN_NEUTRAL.contains(type) || target instanceof Angerable;
        boolean hostile = attacking || type.isIn(HOSTILE)
                || (!type.isIn(FRIENDLY) && !neutral && target instanceof Monster);
        return GazePolicy.priority(recent, hostile, target instanceof PlayerEntity, !type.isIn(FRIENDLY) && neutral);
    }

    public static LivingEntity select(PlayerEntity viewer, UUID current) {
        if (!viewer.isAlive() || viewer.isSpectator() || viewer.isSleeping()) return null;
        var candidates = new ArrayList<GazePolicy.Candidate>();
        var entities = new HashMap<UUID, LivingEntity>();
        for (LivingEntity entity : viewer.getWorld().getEntitiesByClass(LivingEntity.class,
                viewer.getBoundingBox().expand(GazePolicy.RANGE), target -> eligibleWithoutRay(viewer, target))) {
            candidates.add(new GazePolicy.Candidate(entity.getUuid(), priority(viewer, entity), viewer.squaredDistanceTo(entity)));
            entities.put(entity.getUuid(), entity);
        }
        return entities.get(GazePolicy.selectVisible(candidates, current, id -> clearSight(viewer, entities.get(id))));
    }
}
