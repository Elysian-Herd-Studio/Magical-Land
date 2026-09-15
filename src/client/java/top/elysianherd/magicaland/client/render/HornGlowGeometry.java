package top.elysianherd.magicaland.client.render;

import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

final class HornGlowGeometry {
    private static final int SIDES = 12, RINGS = 8;
    private final Map<GeoCube, GeoCube[]> cache = new WeakHashMap<>();

    GeoCube[] shells(GeoCube cube) {
        return cache.computeIfAbsent(cube, source -> new GeoCube[] {
                shell(source, 0.22f), shell(source, 0.70f), shell(source, 1.18f) });
    }

    static Vector3f[] bounds(GeoCube cube) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (var quad : cube.quads()) if (quad != null) for (var vertex : quad.vertices()) {
            min.min(vertex.position()); max.max(vertex.position());
        }
        return new Vector3f[] {min, max};
    }

    private static GeoCube shell(GeoCube cube, float pixels) {
        Vector3f[] bounds = bounds(cube);
        Vector3f center = bounds[0].add(bounds[1], new Vector3f()).mul(0.5f);
        Vector3f half = bounds[1].sub(bounds[0], new Vector3f()).mul(0.5f);
        float rx = half.x * 1.415f + pixels / 16, rz = half.z * 1.415f + pixels / 16;
        float cap = (0.45f + pixels) / 16;
        GeoQuad[] quads = new GeoQuad[SIDES * RINGS];
        for (int ring = 0; ring < RINGS; ring++) for (int side = 0; side < SIDES; side++) {
            GeoVertex[] vertices = {
                    vertex(center, half.y, rx, rz, cap, side, ring),
                    vertex(center, half.y, rx, rz, cap, side + 1, ring),
                    vertex(center, half.y, rx, rz, cap, side + 1, ring + 1),
                    vertex(center, half.y, rx, rz, cap, side, ring + 1) };
            Vector3f edge = vertices[1].position().sub(vertices[0].position(), new Vector3f());
            Vector3f normal = edge.cross(vertices[3].position().sub(vertices[0].position(), new Vector3f()));
            if (normal.lengthSquared() < 1e-12f) normal.set(0, ring == 0 ? -1 : 1, 0);
            else normal.normalize();
            quads[ring * SIDES + side] = new GeoQuad(vertices, normal, null);
        }
        return new GeoCube(quads, cube.pivot(), cube.rotation(), cube.size(), 0, false);
    }

    private static GeoVertex vertex(Vector3f center, float halfHeight, float rx, float rz, float cap, int side, int ring) {
        float u = side / (float) SIDES, v = ring / (float) RINGS;
        double angle = u * Math.PI * 2;
        float radial = ring == 0 || ring == RINGS ? 0.04f : ring == 1 || ring == RINGS - 1 ? 0.82f : 1;
        float y = ring == 0 ? -halfHeight - cap : ring == 1 ? -halfHeight - cap * 0.6f
                : ring == RINGS ? halfHeight + cap : ring == RINGS - 1 ? halfHeight + cap * 0.6f
                : (ring - 2f) / (RINGS - 4) * halfHeight * 2 - halfHeight;
        return new GeoVertex(new Vector3f(center.x + (float) Math.cos(angle) * rx * radial,
                center.y + y, center.z + (float) Math.sin(angle) * rz * radial), u, v);
    }
}
