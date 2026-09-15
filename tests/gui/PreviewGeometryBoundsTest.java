package top.elysianherd.magicaland.client.gui.ponycustom;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.JsonUtil;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;
import top.elysianherd.magicaland.client.render.ManePalette;

public final class PreviewGeometryBoundsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Model raw = JsonUtil.GEO_GSON.fromJson(Files.readString(Path.of(args[0])), Model.class);
        var privateModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
        var worldModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
        var modelField = PreviewGeometryBounds.class.getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(null, privateModel);
        var initialized = PreviewGeometryBounds.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.set(null, true);
        var config = ModelConfig.sanitize(new ModelConfig());
        for (PonyStylePart part : PonyStylePart.values()) for (var style : PonyStyleRegistry.stylesFor(part)) {
            switch (part) {
                case FRONT_MANE -> config.frontManeStyle = style.id;
                case BACK_MANE -> config.backManeStyle = style.id;
                case TAIL -> config.tailStyle = style.id;
                case EYE -> config.eyeStyle = style.id;
            }
            var bounds = PreviewGeometryBounds.framingBounds(config, part);
            check(bounds.width() > .25 && bounds.height() > .25 && bounds.depth() > .1, "nonempty real geometry " + part + style.id);
            check(bounds.width() < 8 && bounds.height() < 8 && bounds.depth() < 8, "no runaway/cross-style bounds");
            check(bounds.center().isFinite(), "finite camera target");
            if (part != PonyStylePart.TAIL) {
                for (String body : new String[] {"Head", "Neck", "LeftEar", "RightEar", "Nose"})
                    check(PreviewGeometryBounds.visible(privateModel.getBone(body).orElseThrow(), config, part), "complete head context");
                check(bounds.minY() <= 17f / 16 && bounds.maxY() >= 30f / 16, "neck and head unclipped");
            }
            for (GeoBone root : privateModel.topLevelBones()) inspect(root, config, part);
        }
        var stable = PreviewGeometryBounds.framingBounds(config, null);
        worldModel.getBone("Head").orElseThrow().setRotX(2);
        check(PreviewGeometryBounds.framingBounds(config, null).equals(stable), "world animation cannot change private bounds");
        Gson gson = new Gson();
        config.bodyColor = "#FF2200";
        config.headColor = "#00DD00";
        config.frontManeColor = "#7A23CC";
        config.backManeColorLocked = true;
        config.irisColor = "#44AA33";
        String before = gson.toJson(config);
        var thumbnail = PonyStyleThumbnails.previewConfig(config, PonyStylePart.BACK_MANE, "01");
        check(thumbnail.bodyColor.endsWith("FFFFFF") && thumbnail.headColor.endsWith("FFFFFF"), "mannequin never takes player body color");
        check(thumbnail.bodyShadingMode.equals("legacy"), "preserve original painted body");
        check(ManePalette.base(thumbnail, ManePalette.Part.BACK) == 0x75659C, "fixed sample accessory tone");
        check(thumbnail.irisColor.equals("#516BD1"), "hair mannequin uses original eye color");
        var eye = PonyStyleThumbnails.previewConfig(config, PonyStylePart.EYE, "03");
        check(eye.eyeStyle.equals("03") && eye.irisColor.equals("#516BD1"), "eye selection with fixed sample tone");
        check(gson.toJson(config).equals(before), "thumbnail does not mutate player config");
        thumbnail.backManeDyeColors[0] = "#AA0000";
        check(gson.toJson(config).equals(before), "dye arrays not shared");
        System.out.println("PASS PreviewGeometryBoundsTest: " + checks + " real geometry, framing, isolation and palette checks");
    }
    private static void inspect(GeoBone bone, ModelConfig config, PonyStylePart part) {
        if (PreviewGeometryBounds.visible(bone, config, part) && !bone.getCubes().isEmpty()) {
            String name = bone.getName();
            check(!name.equals("magic") && !name.equals("Angry") && !name.equals("shut"), "no hidden/animated accessories");
            if (part == PonyStylePart.EYE) check(!name.contains("Mane") && !name.contains("Tail"), "eye mannequin is hairless");
        }
        for (GeoBone child : bone.getChildBones()) inspect(child, config, part);
    }
    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
}
