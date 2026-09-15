import top.elysianherd.magicaland.client.gui.ponycustom.PreviewCamera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PreviewCameraTest {
    private static int checks;
    public static void main(String[] args) {
        var head = new PreviewCamera.Box(-.5f, 1, -.7f, .5f, 2.3f, .4f);
        var tail = new PreviewCamera.Box(-.8f, .1f, .4f, .8f, 1.5f, 2.6f);
        for (int width = 72; width <= 320; width += 16) {
            for (int height = 60; height <= 600; height += 24) {
                for (float yaw = -180; yaw < 180; yaw += 30) {
                    for (float pitch = -80; pitch <= 80; pitch += 20) {
                        for (var box : new PreviewCamera.Box[] {head, tail}) {
                            PreviewCamera camera = new PreviewCamera();
                            camera.angle(yaw, pitch);
                            var pose = camera.update(box, width, height, .016, true);
                            checkFit(box, pose, width, height);
                        }
                    }
                }
            }
        }
        PreviewCamera camera = new PreviewCamera();
        camera.angle(155, -8);
        var start = camera.update(head, 200, 300, .016, true);
        camera.angle(35, 10);
        var next = camera.update(tail, 200, 300, 1.0 / 60, false);
        check(next.z() > start.z() && next.z() < tail.centerZ(), "smooth center");
        check(Math.abs(PreviewCamera.wrap(next.yaw() - start.yaw())) < 30, "smooth yaw");
        check(Math.abs(next.scale() - start.scale()) < start.scale() * .3f, "smooth zoom");
        for (int i = 0; i < 180; i++) next = camera.update(tail, 200, 300, 1.0 / 60, false);
        checkFit(tail, next, 200, 300);
        camera.angle(155, -10);
        var reset = camera.update(head, 200, 300, 0, true);
        check(reset.yaw() == 155 && reset.pitch() == -10 && reset.x() == head.centerX(), "disable autofocus resets immediately");
        camera.manual(179, -8);
        camera.angle(-179, -8);
        var wrapped = camera.update(head, 200, 300, 1.0 / 60, false);
        check(Math.abs(PreviewCamera.wrap(wrapped.yaw() - 179)) < 1, "shortest rotation through 180");
        var thirty = travel(head, tail, 30);
        var sixty = travel(head, tail, 60);
        check(Math.abs(thirty.x() - sixty.x()) < .001f && Math.abs(thirty.z() - sixty.z()) < .001f,
                "time-based centering");
        check(Math.abs(PreviewCamera.wrap(thirty.yaw() - sixty.yaw())) < .001f, "time-based yaw");
        check(Math.abs(thirty.scale() - sixty.scale()) < .15f, "frame-rate stable scale");
        System.out.println("PASS PreviewCameraTest: " + checks + " fit, resize, transition and reset checks.");
    }

    private static PreviewCamera.Pose travel(PreviewCamera.Box head, PreviewCamera.Box tail, int fps) {
        PreviewCamera camera = new PreviewCamera();
        camera.angle(155, -8);
        camera.update(head, 200, 300, .016, true);
        camera.angle(35, 10);
        PreviewCamera.Pose pose = null;
        for (int i = 0; i < fps; i++) pose = camera.update(tail, 200, 300, 1.0 / fps, false);
        return pose;
    }

    private static void checkFit(PreviewCamera.Box b, PreviewCamera.Pose pose, int width, int height) {
        Matrix4f rotation = new Matrix4f().rotateZ((float) Math.PI)
                .rotateY((float) Math.toRadians(pose.yaw())).rotateX((float) Math.toRadians(pose.pitch()));
        for (int i = 0; i < 8; i++) {
            Vector3f point = new Vector3f((i & 1) == 0 ? b.minX() : b.maxX(), (i & 2) == 0 ? b.minY() : b.maxY(),
                    (i & 4) == 0 ? b.minZ() : b.maxZ()).sub(pose.x(), pose.y(), pose.z());
            rotation.transformPosition(point).mul(pose.scale());
            check(Math.abs(point.x) < width * .5f - 3 && Math.abs(point.y) < height * .5f - 3, "complete accessory inside preview");
        }
        check(Float.isFinite(pose.scale()) && pose.scale() > 0, "finite positive scale");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
