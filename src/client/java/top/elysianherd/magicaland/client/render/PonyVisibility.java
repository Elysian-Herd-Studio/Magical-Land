package top.elysianherd.magicaland.client.render;

/** 与原版实体身体的可见性优先级保持一致。 */
public enum PonyVisibility {
    VISIBLE, TRANSLUCENT, OUTLINE, HIDDEN;

    public static PonyVisibility select(boolean preview, boolean visible, boolean visibleToViewer, boolean outline) {
        if (preview || visible) return VISIBLE;
        if (visibleToViewer) return TRANSLUCENT;
        return outline ? OUTLINE : HIDDEN;
    }
}
