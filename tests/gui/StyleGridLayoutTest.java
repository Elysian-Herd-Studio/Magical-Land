import top.elysianherd.magicaland.client.gui.ponycustom.StyleGridLayout;
import top.elysianherd.magicaland.client.config.style.PonyStyleRegistry;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;

public final class StyleGridLayoutTest {
    private static int checks;
    public static void main(String[] args) {
        for (int width = 80; width <= 640; width++) {
            for (PonyStylePart part : PonyStylePart.values()) {
                var styles = PonyStyleRegistry.stylesFor(part);
                var layout = StyleGridLayout.of(width, styles.size());
                check(layout.columns() >= 1 && layout.columns() <= 4, "responsive columns");
                check(layout.height() > 0, "positive height");
                check(layout.indexAt(-1, 0) == -1 && layout.indexAt(0, -1) == -1, "outside rejected");
                check(layout.indexAt(width, 0) == -1, "right edge exclusive");
                for (int i = 0; i < styles.size(); i++) {
                    int x = layout.x(i), y = layout.y(i);
                    check(x + layout.cardWidth() <= width, "card inside width");
                    check(y + StyleGridLayout.CARD_HEIGHT <= layout.height(), "card inside height");
                    check(layout.indexAt(x + layout.cardWidth() / 2, y + 24) == i, "card hit");
                    check(layout.indexAt(x + layout.cardWidth(), y) == -1, "horizontal gaps not selectable");
                    check(layout.indexAt(x, y + StyleGridLayout.CARD_HEIGHT) == -1, "vertical gaps not selectable");
                    check(layout.visible(i, 30, 30 + y, 30 + y + 1), "partially visible card rendered");
                    check(!layout.visible(i, 30, 30 + y + StyleGridLayout.CARD_HEIGHT, 5000), "above viewport skipped");
                    check(!layout.visible(i, 30, -1000, 30 + y), "below viewport skipped");
                    for (int key : new int[] {262,263,264,265,268,269}) {
                        int next = layout.move(i, key);
                        check(next >= 0 && next < styles.size(), "keyboard stays in valid slots");
                    }
                    check(layout.move(i, 268) == 0, "home");
                    check(layout.move(i, 269) == styles.size() - 1, "end");
                }
            }
        }
        var backs = PonyStyleRegistry.stylesFor(PonyStylePart.BACK_MANE);
        check(backs.stream().noneMatch(style -> style.id.equals("07")), "missing back 07 never appears");
        check(backs.get(backs.size() - 1).id.equals("08"), "back 08 retains stable label");
        check(PonyStyleRegistry.stylesFor(PonyStylePart.TAIL).stream().noneMatch(style -> style.id.equals("08")), "missing tail 08 never appears");
        check(StyleGridLayout.of(128, 0).move(0, 262) == -1, "empty gallery keyboard safe");
        System.out.println("PASS StyleGridLayoutTest: " + checks + " responsive, hit, visible-only, keyboard and stable-ID checks.");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
