package top.elysianherd.magicaland.sound;

public final class PlayerStepSoundScope implements AutoCloseable {
    private static final ThreadLocal<PlayerStepSoundScope> CURRENT = new ThreadLocal<>();
    private final PlayerStepSoundScope previous;
    private final Object player, primary, covering;
    private boolean closed;

    private PlayerStepSoundScope(Object player, Object primary, Object covering) {
        this.previous = CURRENT.get();
        this.player = player; this.primary = primary; this.covering = covering;
        CURRENT.set(this);
    }
    public static PlayerStepSoundScope enter(Object player, Object primary, Object covering) {
        return new PlayerStepSoundScope(player, primary, covering);
    }
    public static boolean matches(Object player, Object sound) {
        var scope = CURRENT.get();
        return scope != null && scope.player == player && sound != null
                && (scope.primary == sound || scope.covering == sound);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
