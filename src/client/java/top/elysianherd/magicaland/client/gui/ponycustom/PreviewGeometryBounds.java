package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.FileLoader;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtils;
import top.elysianherd.magicaland.client.animation.PonyExpressions;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.client.config.style.PonyStylePart;
import top.elysianherd.magicaland.client.render.ManeDye;
import top.elysianherd.magicaland.client.render.ManeMirror;

/** 私有静止骨骼；坐标不包含 GeoObjectRenderer 的 (.5,.51,.5) 平移。 */
public final class PreviewGeometryBounds {
    private static final Identifier SOURCE = new Identifier("magicaland", "geo/mare_geo.json");
    private static final Set<String> HIDDEN = Set.of("Smeile", "Style03Smile", "ScrunchedEyes", "Angry",
            "close", "shut", "magic", "Hat", "Dress", "TailDecorate", "Opend");
    private static final Set<String> HEAD = Set.of("Head", "Neck", "LeftEar", "RightEar", "Nose");
    private static BakedGeoModel model;
    private static boolean initialized;
    private static final Map<String, Bounds> CACHED = new LinkedHashMap<>();

    private PreviewGeometryBounds() {}

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public float width() { return maxX - minX; }
        public float height() { return maxY - minY; }
        public float depth() { return maxZ - minZ; }
        public Vector3f center() { return new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2); }
        public Bounds union(Bounds other) {
            return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
    }

    public static Bounds bounds(ModelConfig config, PonyStylePart part) {
        ModelConfig selected = config == null ? new ModelConfig() : config;
        String key = part + ":" + selected.frontManeStyle + ":" + selected.backManeStyle + ":" + selected.tailStyle
                + ":" + selected.eyeStyle + ":" + selected.showHorn + ":" + selected.showWings
                + ":" + selected.frontManeMirrored + ":" + selected.backManeMirrored + ":" + selected.tailMirrored;
        BakedGeoModel source = model();
        Bounds cached = CACHED.get(key);
        if (cached != null) return cached;
        Accumulator found = new Accumulator();
        MatrixStack stack = new MatrixStack();
        for (GeoBone bone : source.topLevelBones()) collect(bone, stack, selected, part, false, found);
        Bounds result = found.result();
        if (CACHED.size() >= 64) CACHED.remove(CACHED.keySet().iterator().next());
        CACHED.put(key, result);
        return result;
    }

    public static Bounds framingBounds(ModelConfig config, PonyStylePart part) {
        return bounds(config, part);
    }

    public static Bounds cutieMarkFramingBounds(ModelConfig config) {
        BakedGeoModel source = model();
        Bounds cached = CACHED.get("CUTIE_MARK");
        if (cached != null) return cached;
        Accumulator found = new Accumulator();
        MatrixStack stack = new MatrixStack();
        for (GeoBone bone : source.topLevelBones()) collect(bone, stack, config, null, true, found);
        Bounds result = found.result();
        CACHED.put("CUTIE_MARK", result);
        return result;
    }

    static BakedGeoModel model() {
        if (!initialized) {
            initialized = true;
            ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                @Override public Identifier getFabricId() { return new Identifier("magicaland", "preview_geometry"); }
                @Override public void reload(ResourceManager manager) { clear(); }
            });
        }
        if (model == null) {
            var raw = FileLoader.loadModelFile(SOURCE, MinecraftClient.getInstance().getResourceManager());
            model = BakedModelFactory.getForNamespace(SOURCE.getNamespace()).constructGeoModel(GeometryTree.fromModel(raw));
            for (GeoBone bone : model.topLevelBones()) neutralize(bone);
        }
        return model;
    }

    public static void clear() {
        model = null;
        CACHED.clear();
    }

    private static void neutralize(GeoBone bone) {
        if (HIDDEN.contains(bone.getName()) || ManeDye.retiredOverlay(bone.getName())) bone.setHidden(true);
        bone.saveInitialSnapshot();
        for (GeoBone child : bone.getChildBones()) neutralize(child);
    }

    static boolean visible(GeoBone bone, ModelConfig config, PonyStylePart part) {
        for (GeoBone ancestor = bone; ancestor != null; ancestor = ancestor.getParent()) {
            if (HIDDEN.contains(ancestor.getName()) || ManeDye.retiredOverlay(ancestor.getName())) return false;
        }
        if (under(bone, "FrontMane")) return (part == null || part == PonyStylePart.FRONT_MANE)
                && underPrefix(bone, "Style" + config.frontManeStyle + "FrontMane");
        if (under(bone, "BackMane")) return (part == null || part == PonyStylePart.BACK_MANE)
                && underPrefix(bone, "Style" + config.backManeStyle + "BackMane");
        if (under(bone, "Tail")) return (part == null || part == PonyStylePart.TAIL)
                && underPrefix(bone, "Style" + config.tailStyle + "Tail");
        if (under(bone, "Emotions")) {
            if (part == PonyStylePart.TAIL) return false;
            var styles = PonyExpressions.eyeStyles();
            var style = styles.getOrDefault(config.eyeStyle, styles.get("01"));
            return style != null && under(bone, style.bones().get("normal"));
        }
        if (under(bone, "Horn")) return part == null && config.showHorn;
        if (under(bone, "Wings")) return part == null && config.showWings && under(bone, "Closed");
        if (part == null) return true;
        if (part == PonyStylePart.TAIL) return bone.getName().equals("Body") || under(bone, "HindLegs");
        return HEAD.contains(bone.getName());
    }

    private static boolean under(GeoBone bone, String name) {
        for (GeoBone cursor = bone; cursor != null; cursor = cursor.getParent()) if (cursor.getName().equals(name)) return true;
        return false;
    }

    private static boolean underPrefix(GeoBone bone, String prefix) {
        for (GeoBone cursor = bone; cursor != null; cursor = cursor.getParent()) if (cursor.getName().startsWith(prefix)) return true;
        return false;
    }

    private static void collect(GeoBone bone, MatrixStack stack, ModelConfig config, PonyStylePart part, boolean marks, Accumulator found) {
        stack.push();
        try {
            if (ManeMirror.rootEnabled(config, bone.getName())) ManeMirror.reflect(stack);
            RenderUtils.prepMatrixForBone(stack, bone);
            if (marks ? bone.getName().equals("Body") || under(bone, "HindLegs") : visible(bone, config, part)) for (var cube : bone.getCubes()) {
                stack.push();
                try {
                    RenderUtils.translateToPivotPoint(stack, cube);
                    RenderUtils.rotateMatrixAroundCube(stack, cube);
                    RenderUtils.translateAwayFromPivotPoint(stack, cube);
                    for (var quad : cube.quads()) if (quad != null) for (var vertex : quad.vertices()) {
                        Vector3f point = stack.peek().getPositionMatrix().transformPosition(new Vector3f(vertex.position()));
                        found.add(point);
                    }
                } finally { stack.pop(); }
            }
            for (GeoBone child : bone.getChildBones()) collect(child, stack, config, part, marks, found);
        } finally { stack.pop(); }
    }

    private static final class Accumulator {
        float x0 = Float.POSITIVE_INFINITY, y0 = x0, z0 = x0;
        float x1 = Float.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        void add(Vector3f point) {
            x0 = Math.min(x0, point.x); y0 = Math.min(y0, point.y); z0 = Math.min(z0, point.z);
            x1 = Math.max(x1, point.x); y1 = Math.max(y1, point.y); z1 = Math.max(z1, point.z);
        }
        Bounds result() {
            return Float.isFinite(x0) ? new Bounds(x0, y0, z0, x1, y1, z1) : new Bounds(-.4f, 0, -.7f, .4f, 2, .7f);
        }
    }
}
