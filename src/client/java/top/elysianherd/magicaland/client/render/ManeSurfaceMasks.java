package top.elysianherd.magicaland.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/** 将独立分色边界映射到原模型表面，沿用原有 UV 和骨骼变换。 */
public final class ManeSurfaceMasks {
    private static final Identifier RESOURCE = new Identifier("magicaland", "mane_dyes/surfaces.json");
    private static final Logger LOGGER = LoggerFactory.getLogger("magicaland/mane-surfaces");
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final double EPSILON = .002;
    private static final Map<GeoBone, Mesh> CACHE = new IdentityHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static Map<String, BoneMask> masks;
    private static boolean failed;

    private ManeSurfaceMasks() {}

    public static Mesh forBone(GeoBone bone) {
        if (failed) return null;
        if (CACHE.containsKey(bone)) return CACHE.get(bone);
        try {
            if (masks == null) {
                try (InputStream input = MinecraftClient.getInstance().getResourceManager()
                        .getResourceOrThrow(RESOURCE).getInputStream()) {
                    byte[] data = input.readNBytes(MAX_BYTES + 1);
                    require(data.length <= MAX_BYTES, "Surface mask exceeds 20 MiB");
                    masks = read(new StringReader(new String(data, StandardCharsets.UTF_8)));
                }
            }
            BoneMask mask = masks.get(bone.getName());
            if (mask == null) return null;
            Mesh mesh = bind(bone, mask);
            if (CACHE.size() >= 128) CACHE.clear();
            CACHE.put(bone, mesh);
            return mesh;
        } catch (Exception failure) {
            if (masks == null) failed = true;
            if (WARNED.size() < 128 && WARNED.add(bone.getName()))
                LOGGER.warn("Surface dye unavailable for {}; using texture mask", bone.getName(), failure);
            if (CACHE.size() >= 128) CACHE.clear();
            CACHE.put(bone, null);
            return null;
        }
    }

    public static void clear() {
        CACHE.clear(); WARNED.clear(); masks = null; failed = false;
    }

    static Map<String, BoneMask> read(Reader input) {
        JsonObject root = JsonParser.parseReader(input).getAsJsonObject();
        require(integer(root.get("version")) == 1, "Unknown surface mask version");
        int width = integer(root.get("texture_width")), height = integer(root.get("texture_height"));
        require(width > 0 && width <= 2048 && height > 0 && height <= 2048, "Invalid texture dimensions");
        JsonObject bones = root.getAsJsonObject("bones");
        require(bones != null && bones.size() <= 128, "Invalid surface bone count");
        Map<String, BoneMask> result = new HashMap<>();
        int faceCount = 0, pointCount = 0;
        for (var entry : bones.entrySet()) {
            JsonObject object = entry.getValue().getAsJsonObject();
            int cubes = integer(object.get("cube_count"));
            require(cubes > 0 && cubes <= 2048, "Invalid cube count");
            List<FaceMask> faces = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonElement faceElement : object.getAsJsonArray("faces")) {
                require(++faceCount <= 20000, "Too many surfaces");
                JsonObject face = faceElement.getAsJsonObject();
                int cube = integer(face.get("cube"));
                String direction = face.get("face").getAsString();
                require(cube >= 0 && cube < cubes && Set.of("west", "east", "north", "south", "up", "down").contains(direction),
                        "Invalid surface identity");
                require(seen.add(cube + "/" + direction), "Duplicate surface");
                double[] bounds = numbers(face.getAsJsonArray("uv_bounds"), 4);
                require(bounds[0] >= -EPSILON && bounds[1] >= -EPSILON && bounds[2] <= width + EPSILON
                        && bounds[3] <= height + EPSILON && bounds[2] > bounds[0] && bounds[3] > bounds[1], "Invalid UV bounds");
                List<Part> parts = new ArrayList<>();
                JsonArray fragments = face.getAsJsonArray("parts");
                require(fragments.size() > 0 && fragments.size() <= 256, "Invalid fragment count");
                for (JsonElement fragment : fragments) {
                    JsonObject part = fragment.getAsJsonObject();
                    int channel = integer(part.get("channel"));
                    JsonArray points = part.getAsJsonArray("uv");
                    require(channel >= 1 && channel <= 6 && points.size() >= 3 && points.size() <= 64, "Invalid surface fragment");
                    double[][] uv = new double[points.size()][];
                    for (int i = 0; i < uv.length; i++) {
                        require(++pointCount <= 300000, "Too many surface vertices");
                        uv[i] = numbers(points.get(i).getAsJsonArray(), 2);
                        require(uv[i][0] >= bounds[0] - EPSILON && uv[i][0] <= bounds[2] + EPSILON
                                && uv[i][1] >= bounds[1] - EPSILON && uv[i][1] <= bounds[3] + EPSILON,
                                "Fragment outside surface");
                    }
                    require(convex(uv), "Surface fragment must be convex");
                    parts.add(new Part(channel, uv));
                }
                double area = parts.stream().mapToDouble(part -> Math.abs(area(part.uv))).sum();
                double expected = (bounds[2] - bounds[0]) * (bounds[3] - bounds[1]);
                require(Math.abs(area - expected) <= Math.max(.00001, expected * .00001), "Incomplete surface coverage");
                for (int i = 0; i < parts.size(); i++) for (int j = 0; j < i; j++)
                    require(overlapArea(parts.get(i).uv, parts.get(j).uv) <= Math.max(1e-7, expected * 1e-8),
                            "Overlapping surface fragments");
                faces.add(new FaceMask(cube, direction, bounds, List.copyOf(parts)));
            }
            result.put(entry.getKey(), new BoneMask(width, height, cubes, List.copyOf(faces)));
        }
        return Map.copyOf(result);
    }

    static Mesh bind(GeoBone bone, BoneMask mask) {
        require(bone.getCubes().size() == mask.cubes, "Model cube count differs from surface mask");
        IdentityHashMap<GeoQuad, List<Fragment>> compiled = new IdentityHashMap<>();
        int quads = 0;
        for (var cube : bone.getCubes()) for (var quad : cube.quads()) if (quad != null) quads++;
        require(quads == mask.faces.size(), "Model face count differs from surface mask");
        for (FaceMask face : mask.faces) {
            GeoQuad quad = null;
            for (GeoQuad candidate : bone.getCubes().get(face.cube).quads())
                if (candidate != null && candidate.direction().asString().equals(face.direction)) {
                    require(quad == null, "Duplicate model direction");
                    quad = candidate;
                }
            require(quad != null && quad.vertices().length == 4, "Missing model surface");
            GeoVertex[] corners = quad.vertices();
            double[][] uv = new double[4][2];
            double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int i = 0; i < 4; i++) {
                uv[i][0] = corners[i].texU() * (double) mask.width;
                uv[i][1] = corners[i].texV() * (double) mask.height;
                bounds[0] = Math.min(bounds[0], uv[i][0]); bounds[1] = Math.min(bounds[1], uv[i][1]);
                bounds[2] = Math.max(bounds[2], uv[i][0]); bounds[3] = Math.max(bounds[3], uv[i][1]);
            }
            for (int i = 0; i < 4; i++) require(Math.abs(bounds[i] - face.bounds[i]) <= EPSILON, "Model UV differs from surface mask");
            double ax = uv[1][0] - uv[0][0], ay = uv[1][1] - uv[0][1];
            double bx = uv[3][0] - uv[0][0], by = uv[3][1] - uv[0][1], determinant = ax * by - ay * bx;
            require(Math.abs(determinant) > 1e-10, "Degenerate model UV");
            require(Math.abs(uv[2][0] - uv[0][0] - ax - bx) < EPSILON
                    && Math.abs(uv[2][1] - uv[0][1] - ay - by) < EPSILON, "Non-affine model UV");
            List<Fragment> fragments = new ArrayList<>();
            for (Part part : face.parts) {
                List<GeoVertex> polygon = new ArrayList<>();
                for (double[] point : part.uv) {
                    double x = point[0] - uv[0][0], y = point[1] - uv[0][1];
                    double a = (x * by - y * bx) / determinant, b = (ax * y - ay * x) / determinant;
                    Vector3f position = new Vector3f(corners[0].position())
                            .fma((float) a, new Vector3f(corners[1].position()).sub(corners[0].position()))
                            .fma((float) b, new Vector3f(corners[3].position()).sub(corners[0].position()));
                    polygon.add(new GeoVertex(position, (float) (point[0] / mask.width), (float) (point[1] / mask.height)));
                }
                if (area(part.uv) * area(uv) < 0) Collections.reverse(polygon);
                List<GeoVertex> vertices = new ArrayList<>();
                if (polygon.size() == 4) vertices.addAll(polygon);
                else for (int i = 1; i + 1 < polygon.size(); i++) {
                    vertices.add(polygon.get(0)); vertices.add(polygon.get(i));
                    vertices.add(polygon.get(i + 1)); vertices.add(polygon.get(i + 1));
                }
                fragments.add(new Fragment(part.channel, vertices.toArray(GeoVertex[]::new)));
            }
            require(compiled.put(quad, List.copyOf(fragments)) == null, "Duplicate compiled quad");
        }
        return new Mesh(compiled);
    }

    public static final class Mesh {
        private final IdentityHashMap<GeoQuad, List<Fragment>> fragments;
        private Mesh(IdentityHashMap<GeoQuad, List<Fragment>> fragments) { this.fragments = fragments; }

        public boolean emit(GeoQuad quad, Matrix4f matrix, Vector3f normal, VertexConsumer buffer,
                int light, int overlay, float red, float green, float blue, float alpha,
                boolean mirrored, int[] slots, int columns, int rows) {
            List<Fragment> parts = fragments.get(quad);
            if (parts == null) return false;
            require(slots.length >= 6 && columns > 0 && rows > 0, "Invalid surface palette");
            for (Fragment part : parts) {
                int slot = slots[part.channel - 1];
                require(slot >= 0 && slot < columns * rows, "Invalid palette slot");
                for (int start = 0; start < part.vertices.length; start += 4) for (int i = 0; i < 4; i++) {
                    GeoVertex vertex = part.vertices[start + (mirrored ? 3 - i : i)];
                    Vector3f position = matrix.transformPosition(new Vector3f(vertex.position()));
                    buffer.vertex(position.x, position.y, position.z, red, green, blue, alpha,
                            (vertex.texU() + slot % columns) / columns, (vertex.texV() + slot / columns) / rows,
                            overlay, light, normal.x, normal.y, normal.z);
                }
            }
            return true;
        }
    }

    private record Fragment(int channel, GeoVertex[] vertices) {}
    record BoneMask(int width, int height, int cubes, List<FaceMask> faces) {}
    record FaceMask(int cube, String direction, double[] bounds, List<Part> parts) {}
    record Part(int channel, double[][] uv) {}

    private static double[] numbers(JsonArray values, int length) {
        require(values != null && values.size() == length, "Invalid coordinate count");
        double[] result = new double[length];
        for (int i = 0; i < length; i++) { result[i] = values.get(i).getAsDouble(); require(Double.isFinite(result[i]), "Non-finite coordinate"); }
        return result;
    }

    private static int integer(JsonElement value) {
        double number = value.getAsDouble();
        require(Double.isFinite(number) && number == (int) number, "Expected integer");
        return (int) number;
    }

    private static double area(double[][] polygon) {
        double result = 0;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++)
            result += polygon[j][0] * polygon[i][1] - polygon[i][0] * polygon[j][1];
        return result * .5;
    }

    private static boolean convex(double[][] polygon) {
        double orientation = Math.signum(area(polygon));
        if (orientation == 0) return false;
        for (int i = 0; i < polygon.length; i++) {
            double[] a = polygon[i], b = polygon[(i + 1) % polygon.length], c = polygon[(i + 2) % polygon.length];
            double cross = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]);
            if (cross * orientation < -1e-8) return false;
        }
        return true;
    }

    private static double overlapArea(double[][] first, double[][] second) {
        List<double[]> polygon = new ArrayList<>(List.of(first));
        double orientation = Math.signum(area(second));
        for (int i = 0; i < second.length && !polygon.isEmpty(); i++) {
            double[] edge = second[i], end = second[(i + 1) % second.length];
            List<double[]> next = new ArrayList<>();
            double[] previous = polygon.get(polygon.size() - 1);
            double previousSide = side(edge, end, previous) * orientation;
            for (double[] current : polygon) {
                double currentSide = side(edge, end, current) * orientation;
                if ((previousSide >= 0) != (currentSide >= 0)) {
                    double amount = previousSide / (previousSide - currentSide);
                    next.add(new double[] {previous[0] + (current[0] - previous[0]) * amount,
                            previous[1] + (current[1] - previous[1]) * amount});
                }
                if (currentSide >= 0) next.add(current);
                previous = current; previousSide = currentSide;
            }
            polygon = next;
        }
        return polygon.size() < 3 ? 0 : Math.abs(area(polygon.toArray(double[][]::new)));
    }

    private static double side(double[] a, double[] b, double[] point) {
        return (b[0] - a[0]) * (point[1] - a[1]) - (b[1] - a[1]) * (point[0] - a[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
