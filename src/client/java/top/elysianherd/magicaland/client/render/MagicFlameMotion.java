package top.elysianherd.magicaland.client.render;

/** 每 tick 采样实际位移；渲染只插值，不积累帧率相关的运动。 */
final class MagicFlameMotion {
    record Point(double x, double y, double z) {
        static final Point ZERO = new Point(0, 0, 0);
        boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
        Point mix(Point other, double t) { return new Point(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t); }
    }
    record Frame(Point tail, float radius, float sway, float phase) {}
    private Point position, previousVelocity = Point.ZERO, velocity = Point.ZERO;
    private int tick;

    void tick(int age, Point next) {
        if (next == null || !next.finite()) { position = null; previousVelocity = velocity = Point.ZERO; return; }
        if (position != null && age == tick) return;
        int elapsed = age - tick;
        if (position == null || elapsed <= 0 || elapsed > 4) { reset(age, next); return; }
        Point travel = new Point(next.x - position.x, next.y - position.y, next.z - position.z);
        if (travel.length() > 4) { reset(age, next); return; }
        double divisor = elapsed * Math.max(1, travel.length() / elapsed / .9);
        Point measured = new Point(travel.x / divisor, travel.y / divisor, travel.z / divisor);
        previousVelocity = velocity;
        velocity = velocity.mix(measured, 1 - Math.pow(.52, elapsed));
        position = next;
        tick = age;
    }

    private void reset(int age, Point next) { tick = age; position = next; previousVelocity = velocity = Point.ZERO; }
    Frame sample(float delta, double ticks, int seed) {
        double blend = Float.isFinite(delta) ? Math.max(0, Math.min(1, delta)) : 0;
        return frame(previousVelocity.mix(velocity, blend), ticks, seed);
    }
    static Frame frame(Point movement, double ticks, int seed) {
        if (movement == null || !movement.finite()) movement = Point.ZERO;
        if (!Double.isFinite(ticks)) ticks = 0;
        double speed = movement.length();
        double phase = (((ticks % 240) + 240) % 240) * Math.PI / 120 + Math.floorMod(seed, 251) * Math.PI * 2 / 251;
        Point tail = new Point(-movement.x * .95, .23 / (1 + speed * 3) - movement.y * .95, -movement.z * .95);
        double length = tail.length();
        if (length > 1.05) tail = new Point(tail.x / length * 1.05, tail.y / length * 1.05, tail.z / length * 1.05);
        float radius = (float) (.095 * (1 + .035 * Math.sin(phase * 2) + .025 * Math.sin(phase * 3 + .7)));
        return new Frame(tail, radius, (float) (.018 * Math.sin(phase * 2 + .6)), (float) phase);
    }
}
