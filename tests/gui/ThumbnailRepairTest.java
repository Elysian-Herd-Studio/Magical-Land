package top.elysianherd.magicaland.client.gui.ponycustom;

import com.google.gson.Gson;
import java.lang.reflect.Modifier;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;

public final class ThumbnailRepairTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        ModelConfig source = ModelConfig.sanitize(new ModelConfig());
        for (PonyStylePart part : PonyStylePart.values()) for (var style : PonyStyleRegistry.stylesFor(part)) {
            String expected = gson.toJson(PonyStyleThumbnails.previewConfig(source, part, style.id));
            String mirroredField = switch (part) {
                case FRONT_MANE -> "frontManeMirrored";
                case BACK_MANE -> "backManeMirrored";
                case TAIL -> "tailMirrored";
                case EYE -> "";
            };
            for (var field : ModelConfig.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Object before = field.get(source);
                if (field.getType() == String.class) field.set(source, "#12AF67");
                else if (field.getType() == boolean.class) field.set(source, !field.getBoolean(source));
                else if (field.getType() == int.class) field.set(source, 73);
                else if (field.getType() == String[].class) field.set(source, new String[] {"#123456"});
                String unchanged = gson.toJson(source);
                ModelConfig thumbnail = PonyStyleThumbnails.previewConfig(source, part, style.id);
                if (field.getName().equals(mirroredField)) {
                    check(field.getBoolean(thumbnail) == field.getBoolean(source), "thumbnail follows " + mirroredField);
                    field.set(thumbnail, before);
                }
                check(expected.equals(gson.toJson(thumbnail)), "fixed palette ignores " + field.getName());
                check(unchanged.equals(gson.toJson(source)), "thumbnail cannot mutate " + field.getName());
                field.set(source, before);
            }
        }
        for (int width = 4; width <= 4096; width += 7) for (int height = 4; height <= 1024; height += 13) {
            ThumbnailSize image = ThumbnailSize.of(width, height);
            check(image.width() >= 1 && image.height() >= 1 && image.width() <= 192 && image.height() <= 192, "bounded image");
            var area = image.fit(width, height);
            check(area.x() >= -.001 && area.y() >= -.001 && area.width() <= width + .001 && area.height() <= height + .001, "contain within card");
            check(Math.abs(area.width() / image.width() - area.height() / image.height()) < .001, "same horizontal and vertical blit scale");
        }
        for (int width : new int[] {84, 96, 125, 232, 960}) {
            var image = ThumbnailSize.of(width, 49);
            var area = image.fit(width, 49);
            check(Math.abs(area.width() / image.width() - area.height() / image.height()) < .00001, "wide card regression " + width);
        }
        System.out.println("PASS ThumbnailRepairTest: " + checks + " fixed-palette isolation and aspect-ratio checks.");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
