package top.elysianherd.magicaland.client.config.style;

/**
 * The distinct pony body parts that can independently pick a "style" (a set of
 * geo bones + textures). Each part has its own bone-name suffix (used to build
 * the "Style{id}{suffix}" bone prefix matched in the renderer) and its own
 * generic display-name translation key (shown to players as "{noun}{ordinal}",
 * e.g. "发型01" — never the underlying style id or legacy character code).
 */
public enum PonyStylePart {
    FRONT_MANE("FrontMane", "text.magicaland.style.generic.mane"),
    BACK_MANE("BackMane", "text.magicaland.style.generic.mane"),
    TAIL("Tail", "text.magicaland.style.generic.tail"),
    EYE("CommonFace", "text.magicaland.style.generic.eye");

    public final String boneSuffix;
    public final String genericNameLangKey;

    PonyStylePart(String boneSuffix, String genericNameLangKey) {
        this.boneSuffix = boneSuffix;
        this.genericNameLangKey = genericNameLangKey;
    }
}
