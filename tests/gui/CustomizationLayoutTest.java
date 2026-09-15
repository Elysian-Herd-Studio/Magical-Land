import top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout;

public final class CustomizationLayoutTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int width = 320; width <= 1920; width += 8) {
            for (int height = 240; height <= 1080; height += 12) {
                var layout = CustomizationLayout.of(8, 31, width - 16, height - 39);
                var preview = layout.preview();
                var model = layout.model();
                var details = layout.details();
                check(preview.width() >= 104, "preview minimum width");
                check(details.width() >= 158, "controls minimum width");
                check(preview.right() < details.x(), "independent columns");
                check(details.right() <= width - 8, "right edge");
                check(preview.bottom() <= height - 8 && details.bottom() <= height - 8, "bottom edge");
                check(model.y() >= preview.y() + 30, "auto-focus control excluded from dragging");
                check(model.bottom() <= preview.bottom(), "preview stays within panel");
                check(model.height() >= 80, "usable rotatable preview");
                check(!model.contains(model.right(), model.y()), "right edge exclusive");
                check(!model.contains(model.x(), model.bottom()), "bottom edge exclusive");
                check(model.contains(model.x(), model.y()), "inside hit test");
                for (int i = 0; i < 6; i++) {
                    int x = 8 + i % layout.tabColumns() * (layout.tabWidth() + 4);
                    int y = layout.tabsY() + i / layout.tabColumns() * 23;
                    check(x + layout.tabWidth() <= width - 8, "tab fits width");
                    check(y + 20 < preview.y(), "tabs above columns");
                }
                checks += 23;
            }
        }
        System.out.println("PASS CustomizationLayoutTest: " + checks + " layout and hit-target checks (320x240 to 1920x1080).");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
