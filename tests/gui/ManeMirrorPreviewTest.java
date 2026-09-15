package top.elysianherd.magicaland.client.gui.ponycustom;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;
import software.bernie.geckolib.util.RenderUtils;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;
import top.elysianherd.magicaland.client.render.ManeMirror;

public final class ManeMirrorPreviewTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path repo = Path.of(args[0]);
        Model raw = JsonUtil.GEO_GSON.fromJson(Files.readString(repo.resolve("src/main/resources/assets/magicaland/geo/mare_geo.json")), Model.class);
        var model = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
        var field = PreviewGeometryBounds.class.getDeclaredField("model"); field.setAccessible(true); field.set(null, model);
        field = PreviewGeometryBounds.class.getDeclaredField("initialized"); field.setAccessible(true); field.set(null, true);
        for (PonyStylePart part : new PonyStylePart[] {PonyStylePart.FRONT_MANE, PonyStylePart.BACK_MANE, PonyStylePart.TAIL}) {
            int changedBounds = 0;
            for (var style : PonyStyleRegistry.stylesFor(part)) {
                ModelConfig config = ModelConfig.sanitize(new ModelConfig());
                switch (part) {
                    case FRONT_MANE -> config.frontManeStyle = style.id;
                    case BACK_MANE -> config.backManeStyle = style.id;
                    case TAIL -> config.tailStyle = style.id;
                    default -> {}
                }
                var original = PreviewGeometryBounds.bounds(config, part);
                ManeMirror.set(config, part, true);
                var mirrored = PreviewGeometryBounds.bounds(config, part);
                float[] expected = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
                for (GeoBone root : model.topLevelBones()) expected(root, new MatrixStack(), config, part, false, expected);
                near(mirrored.minX(), expected[0]); near(mirrored.minY(), expected[1]); near(mirrored.minZ(), expected[2]);
                near(mirrored.maxX(), expected[3]); near(mirrored.maxY(), expected[4]); near(mirrored.maxZ(), expected[5]);
                if (Math.abs(mirrored.minX() - original.minX()) + Math.abs(mirrored.maxX() - original.maxX()) > .01) changedBounds++;
                check(mirrored == PreviewGeometryBounds.bounds(config, part), "mirrored framing cache is stable");
                ManeMirror.set(config, part, false);
                check(original == PreviewGeometryBounds.bounds(config, part), "unmirroring restores original framing key");
                ManeMirror.set(config, part, true);
                var thumbnail = PonyStyleThumbnails.previewConfig(config, part, style.id);
                check(ManeMirror.enabled(thumbnail, part), "thumbnail follows mirror setting for " + part + style.id);
                check(thumbnail.bodyColor.equals("#FFFFFFFF") && thumbnail.irisColor.equals("#516BD1"), "mannequin colors remain fixed");
                for (var other : PonyStylePart.values()) if (other != part)
                    check(!ManeMirror.enabled(thumbnail, other), "thumbnail does not mirror unrelated components");
            }
            check(changedBounds > 0, "asymmetric " + part + " styles actually move across the head/body");
        }
        ModelConfig config = ModelConfig.sanitize(new ModelConfig());
        var eyeBounds = PreviewGeometryBounds.bounds(config, PonyStylePart.EYE);
        config.frontManeMirrored = config.backManeMirrored = config.tailMirrored = true;
        check(eyeBounds.equals(PreviewGeometryBounds.bounds(config, PonyStylePart.EYE)), "hairless eye framing is unchanged");
        check(!PonyStyleThumbnails.previewConfig(config, PonyStylePart.EYE, "03").tailMirrored, "eye mannequin unmirrored");
        var key = Class.forName(PonyStyleThumbnails.class.getName() + "$Key").getDeclaredConstructors()[0]; key.setAccessible(true);
        check(!key.newInstance(PonyStylePart.FRONT_MANE, "01", 64, 64, false)
                .equals(key.newInstance(PonyStylePart.FRONT_MANE, "01", 64, 64, true)), "thumbnail cache distinguishes mirrored and original styles");
        System.out.println("PASS ManeMirrorPreviewTest: " + checks + " checks");
    }

    private static void expected(GeoBone bone, MatrixStack stack, ModelConfig config, PonyStylePart part, boolean mirror, float[] found) {
        boolean mirrored = mirror || ManeMirror.rootEnabled(config, bone.getName());
        stack.push();
        try {
            RenderUtils.prepMatrixForBone(stack, bone);
            if (PreviewGeometryBounds.visible(bone, config, part)) for (var cube : bone.getCubes()) {
                stack.push();
                try {
                    RenderUtils.translateToPivotPoint(stack, cube); RenderUtils.rotateMatrixAroundCube(stack, cube); RenderUtils.translateAwayFromPivotPoint(stack, cube);
                    for (var quad : cube.quads()) if (quad != null) for (var v : quad.vertices()) {
                        Vector3f p = stack.peek().getPositionMatrix().transformPosition(new Vector3f(v.position()));
                        if (mirrored) p.x = -p.x;
                        found[0] = Math.min(found[0], p.x); found[1] = Math.min(found[1], p.y); found[2] = Math.min(found[2], p.z);
                        found[3] = Math.max(found[3], p.x); found[4] = Math.max(found[4], p.y); found[5] = Math.max(found[5], p.z);
                    }
                } finally { stack.pop(); }
            }
            for (var child : bone.getChildBones()) expected(child, stack, config, part, mirrored, found);
        } finally { stack.pop(); }
    }
    private static void near(float a, float b) { check(Math.abs(a - b) < 1e-5, "mirror framing uses actual geometry: " + a + " / " + b); }
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }
}
