package top.elysianherd.magicaland.client.config.style;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PonyStyleRegistry {
    public static final String DEFAULT_ID = "01";

    private static final Map<PonyStylePart, List<PonyStyleDefinition>> STYLES =
            new EnumMap<>(PonyStylePart.class);
    private static final Map<PonyStylePart, Map<String, String>> LEGACY_IDS =
            new EnumMap<>(PonyStylePart.class);

    static {
        for (PonyStylePart part : PonyStylePart.values()) {
            STYLES.put(part, new ArrayList<>());
            LEGACY_IDS.put(part, new HashMap<>());
        }

        register(PonyStylePart.FRONT_MANE, "01", "TS");
        register(PonyStylePart.FRONT_MANE, "02", "RD");
        register(PonyStylePart.FRONT_MANE, "03", "RR");
        register(PonyStylePart.FRONT_MANE, "04", "PP");
        register(PonyStylePart.FRONT_MANE, "05", "AJ");
        register(PonyStylePart.FRONT_MANE, "06", "FS");
        register(PonyStylePart.FRONT_MANE, "07");
        register(PonyStylePart.FRONT_MANE, "08");

        register(PonyStylePart.BACK_MANE, "01", "TS");
        register(PonyStylePart.BACK_MANE, "02", "RD");
        register(PonyStylePart.BACK_MANE, "03", "RR");
        register(PonyStylePart.BACK_MANE, "04", "PP");
        register(PonyStylePart.BACK_MANE, "05", "AJ");
        register(PonyStylePart.BACK_MANE, "06", "FS");
        // 07 后发暂缓制作，保留编号；08 丸子头不再改号。
        register(PonyStylePart.BACK_MANE, "08");

        register(PonyStylePart.TAIL, "01", "TS");
        register(PonyStylePart.TAIL, "02", "RD");
        register(PonyStylePart.TAIL, "03", "RR");
        register(PonyStylePart.TAIL, "04", "PP");
        register(PonyStylePart.TAIL, "05", "AJ");
        register(PonyStylePart.TAIL, "06", "FS");
        register(PonyStylePart.TAIL, "07");

        register(PonyStylePart.EYE, "01", "TS");
        register(PonyStylePart.EYE, "02", "RR");
        register(PonyStylePart.EYE, "03", "FS");
    }

    private PonyStyleRegistry() {
    }

    private static void register(PonyStylePart part, String id, String... legacyCodes) {
        if (byId(part, id) != null) {
            throw new IllegalArgumentException("Duplicate style id " + part + ":" + id);
        }

        STYLES.get(part).add(new PonyStyleDefinition(part, id));
        for (String legacyCode : legacyCodes) {
            LEGACY_IDS.get(part).put(legacyCode, id);
        }
    }

    public static List<PonyStyleDefinition> stylesFor(PonyStylePart part) {
        return List.copyOf(STYLES.get(part));
    }

    public static PonyStyleDefinition byId(PonyStylePart part, String id) {
        if (id == null) {
            return null;
        }
        for (PonyStyleDefinition style : STYLES.get(part)) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        return null;
    }

    public static boolean isValidStyleId(PonyStylePart part, String id) {
        return byId(part, id) != null;
    }

    public static String legacyCodeToId(PonyStylePart part, String legacyCode) {
        return legacyCode == null ? null : LEGACY_IDS.get(part).get(legacyCode);
    }

    public static int displayOrdinal(PonyStylePart part, String id) {
        List<PonyStyleDefinition> styles = STYLES.get(part);
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id.equals(id)) {
                return i + 1;
            }
        }
        return -1;
    }
}
