package top.elysianherd.magicaland.client.model;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.Animation;
import top.elysianherd.magicaland.client.animation.PonyIdleEarAnimations;
import top.elysianherd.magicaland.client.animation.PonyFlightAnimations;
import top.elysianherd.magicaland.client.animation.PonyEmoteAnimations;

public class GeckoPlayerModel extends GeoModel<GeckoPlayerAnimatable> {
    private static final Identifier MODEL = new Identifier("magicaland", "geo/mare_geo.json");
    private static final Identifier ANIMATION = new Identifier("magicaland", "animations/mare_animation.json");

    @Override
    public Identifier getModelResource(GeckoPlayerAnimatable object) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeckoPlayerAnimatable object) {
        return new Identifier("magicaland", "textures/entity/base.png");
    }

    @Override
    public Identifier getAnimationResource(GeckoPlayerAnimatable object) {
        return ANIMATION;
    }

    @Override
    public Animation getAnimation(GeckoPlayerAnimatable object, String name) {
        if (PonyEmoteAnimations.OPEN_EYES.equals(name)) return PonyEmoteAnimations.openEyes();
        if ("Ballet".equals(name))
            return PonyEmoteAnimations.resolveBallet(super.getAnimation(object, name));
        if (PonyFlightAnimations.NAME.equals(name))
            return PonyFlightAnimations.resolve(super.getAnimation(object, "fly"));
        if (PonyIdleEarAnimations.internal(name))
            return PonyIdleEarAnimations.resolve(super.getAnimation(object, "ear_parallel"), name);
        return super.getAnimation(object, name);
    }
}
