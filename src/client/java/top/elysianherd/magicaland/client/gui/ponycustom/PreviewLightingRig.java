package top.elysianherd.magicaland.client.gui.ponycustom;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import top.elysianherd.magicaland.client.config.Config;

public final class PreviewLightingRig {
    private static final String[] MODES = {"balanced", "soft", "dramatic"};

    private PreviewLightingRig() {}

    public static void apply() {
        switch (mode()) {
            case "soft" -> RenderSystem.setShaderLights(new Vector3f(-.35f, -.45f, .82f),
                    new Vector3f(.15f, -.15f, .98f));
            case "dramatic" -> RenderSystem.setShaderLights(new Vector3f(-.75f, -.25f, .61f),
                    new Vector3f(.08f, .15f, .98f));
            default -> RenderSystem.setShaderLights(new Vector3f(-.5f, -.5f, .7071068f),
                    new Vector3f(.35f, -.20f, .9151503f));
        }
    }

    public static String mode() {
        String mode = Config.getInstance().previewLighting;
        return indexOf(mode) < 0 ? MODES[0] : mode;
    }

    public static int modeIndex() {
        return indexOf(mode());
    }

    public static int modeCount() {
        return MODES.length;
    }

    public static String nextMode() {
        String next = MODES[(modeIndex() + 1) % MODES.length];
        Config.getInstance().previewLighting = next;
        return next;
    }

    private static int indexOf(String mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return i;
        return -1;
    }
}
