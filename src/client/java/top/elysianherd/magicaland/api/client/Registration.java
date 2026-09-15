package top.elysianherd.magicaland.api.client;

/** Client-thread registration. Closing repeatedly is safe. */
public interface Registration extends AutoCloseable {
    String ownerId();
    int priority();
    boolean isRegistered();
    @Override void close();
}
