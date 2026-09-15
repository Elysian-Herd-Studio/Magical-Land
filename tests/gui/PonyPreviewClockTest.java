package top.elysianherd.magicaland.client.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import top.elysianherd.magicaland.client.animation.PonyIdleEars;

public final class PonyPreviewClockTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        clockEdges();
        List<String> reference = null;
        for (int fps : new int[] {20, 30, 60, 144}) {
            var clock = new PonyPreviewClock();
            var ears = new PonyIdleEars();
            var owner = new Object();
            UUID seed = new UUID(71, 122);
            List<String> events = new ArrayList<>();
            long lastWindow = Long.MIN_VALUE;
            for (int frame = 0; frame <= fps * 90; frame++) {
                long nanos = 5_000_000_000L + Math.round(frame * 1e9 / fps);
                double ticks = clock.sample(nanos);
                near(ticks, frame * 20d / fps, 3e-8, "UI elapsed time advances while game age stays fixed");
                var event = ears.sample(owner, owner, seed, ticks, true);
                for (int pass = 0; pass < 3; pass++) {
                    near(clock.sample(nanos), ticks, 0, "repeated render sample does not advance");
                    check(java.util.Objects.equals(event, ears.sample(owner, owner, seed, clock.ticks(), true)),
                            "repeated render does not reroll ears");
                }
                if (event != null && event.window() != lastWindow) {
                    events.add(event.window() + ":" + event.variant());
                    lastWindow = event.window();
                }
            }
            check(!events.isEmpty() && events.size() < 18, "probability ears trigger but not in every window");
            if (reference == null) reference = events;
            else check(reference.equals(events), "same UI time and seed has same ear events at all frame rates");
        }
        independentPreview();
        sourceContract(Path.of(args[0]));
        System.out.println("PASS PonyPreviewClockTest: " + checks + " checks");
    }

    private static void clockEdges() {
        var clock = new PonyPreviewClock();
        near(clock.sample(100), 0, 0, "first render starts at zero");
        near(clock.sample(50_000_100), 1, 0, "one UI tick");
        near(clock.sample(25_000_100), 1, 0, "backward input is ignored");
        near(clock.sample(75_000_100), 1.5, 0, "backward input does not count time twice");
        near(clock.sample(100_000_000_100L), 6.5, 0, "long background gap is bounded to five animation ticks");
        clock.reset();
        near(clock.ticks(), 0, 0, "exit clears clock");
        near(clock.sample(-1_000_000), 0, 0, "nanoTime origin may be negative");
        near(clock.sample(49_000_000), 1, 0, "negative origin still advances");
        clock.reset();
        clock.sample(Long.MAX_VALUE - 24_999_999);
        near(clock.sample(Long.MIN_VALUE + 25_000_000), 1, 0, "nanoTime wrap keeps elapsed interval");
    }

    private static void independentPreview() {
        var preview = new PonyPreviewAnimatable();
        var second = new PonyPreviewAnimatable();
        check(preview.shouldPlayAnimsWhileGamePaused(), "preview explicitly continues through pause");
        check(preview.getPlayer() == null && preview.allowsAutomaticGaze(), "title preview supports mouse gaze without player");
        preview.syncLocalAnimationState(null);
        check(preview.getPlayer() == null, "preview sync is a no-op");
        check(preview.getAnimatableInstanceCache() != second.getAnimatableInstanceCache(), "caches are not shared");
        AnimatableManager<GeckoPlayerAnimatable> manager = preview.getAnimatableInstanceCache().getManagerForId(17);
        check(manager.getAnimationControllers().keySet().equals(java.util.Set.of("idle_preview", "blink_parallel_preview",
                "tail_parallel_preview", "ear_preview", "expression_controller")), "only preview controllers registered");
        preview.beginFrame(1_000_000_000);
        for (int frame = 0; frame < 120; frame++) {
            preview.beginFrame(1_000_000_000L + frame * 10_000_000L);
            double expected = frame * .2;
            for (float partial : new float[] {0, .125f, .75f, .999f}) {
                var state = new AnimationState<GeckoPlayerAnimatable>(preview, 0, 0, partial, false);
                preview.prepareAnimationFrame(17, state);
                near(state.getData(DataTickets.TICK), expected, 1e-10, "DataTickets.TICK receives full UI time once");
                near(manager.getFirstTickTime(), 0, 0, "first-time baseline excludes Minecraft partial tick");
                near(preview.getTick(null), expected, 1e-10, "getTick reads the already sampled UI clock");
            }
            near(second.getTick(null), 0, 0, "other preview clock never changes");
        }
        preview.reset();
        near(preview.getTick(null), 0, 0, "exit resets preview age");
        check(preview.getPlayer() == null, "exit releases cosmetic player reference");
    }

    private static void sourceContract(Path root) throws Exception {
        String custom = Files.readString(root.resolve("src/client/java/top/elysianherd/magicaland/client/gui/PonyCustom.java"));
        String preview = Files.readString(root.resolve("src/client/java/top/elysianherd/magicaland/client/model/PonyPreviewAnimatable.java"));
        String renderer = Files.readString(root.resolve("src/client/java/top/elysianherd/magicaland/client/gui/ponycustom/PonyPreviewRenderer.java"));
        String init = custom.substring(custom.indexOf("private void initRenderer()"), custom.indexOf("private void renderGrassBlockPreview"));
        check(init.contains("if (ponyRenderer == null)"), "page/color reinit reuses preview instance");
        check(custom.contains("ponyRenderer.close()") && custom.contains("ponyRenderer = null;")
                && renderer.contains("ponyAnimatable.reset()"), "exit discards animation manager and model time together");
        check(renderer.contains("prepareAnimationFrame(instanceId, state)") && renderer.contains("super.handleAnimations(animatable, instanceId, state)"),
                "only the local preview model uses the custom tick");
        check(!preview.contains("ClientNetworkHandler") && !preview.contains("getWorld()") && !preview.contains("player.age"),
                "preview animation and ears have no world/network clock dependency");
        check(preview.contains("PonyIdleEarAnimations.raw(event.variant())") && !custom.contains("\"ear_parallel\""),
                "preview reuses authored probabilistic ear variants rather than fixed old loop");
        check(renderer.contains("try (var gaze = PonyGuiGaze.begin(this, ponyAnimatable.getPlayer(), mouseX, mouseY,"),
                "mouse gaze is scoped to the main pony draw");
        String thumbnails = Files.readString(root.resolve("src/client/java/top/elysianherd/magicaland/client/gui/ponycustom/PonyStyleThumbnails.java"));
        check(!thumbnails.contains("PonyPreviewAnimatable") && !thumbnails.contains("PonyGuiGaze.begin"), "static thumbnail path stays independent");
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
