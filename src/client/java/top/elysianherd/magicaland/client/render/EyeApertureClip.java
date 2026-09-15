package top.elysianherd.magicaland.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 在眼眶局部 XY 窗口内裁剪原面；不修改模型，也不依赖深度遮挡。 */
public final class EyeApertureClip {
    private static final double EPS = 1e-9;
    private static final int MAX_CELLS = 4096;

    public record Point(double x, double y) {}
    public record Vertex(double x, double y, double z, double u, double v) {}

    private EyeApertureClip() {}

    public static Aperture prepare(List<List<Point>> regions) {
        if (regions == null) throw new IllegalArgumentException("Aperture regions cannot be null");
        List<Cell> cells = new ArrayList<>();
        for (List<Point> region : regions) {
            List<Point> polygon = normalize(region);
            List<List<Point>> pieces = List.of(polygon);
            for (Cell cell : cells) {
                List<List<Point>> next = new ArrayList<>();
                for (List<Point> piece : pieces) next.addAll(subtract(piece, cell));
                if (next.size() + cells.size() > MAX_CELLS)
                    throw new IllegalArgumentException("Aperture union is too complex");
                pieces = next;
                if (pieces.isEmpty()) break;
            }
            for (List<Point> piece : pieces) cells.add(new Cell(piece));
            if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("Aperture union is too complex");
        }
        return new Aperture(List.copyOf(cells));
    }

    public static final class Aperture {
        private final List<Cell> cells;
        private Aperture(List<Cell> cells) { this.cells = cells; }
        public int regionCount() { return cells.size(); }
        public boolean isEmpty() { return cells.isEmpty(); }

        public List<List<Vertex>> clipQuad(Vertex a, Vertex b, Vertex c, Vertex d) {
            if (cells.isEmpty() || !finite(a) || !finite(b) || !finite(c) || !finite(d)) return List.of();
            List<Vertex> quad = cleanVertices(List.of(a, b, c, d));
            if (!hasArea(quad)) return List.of();
            Line line = projectedLine(quad);
            if (line != null) return clipSide(quad, line);
            List<List<Vertex>> result = new ArrayList<>();
            for (Cell cell : cells) {
                if (cell.outside(quad)) continue;
                List<Vertex> clipped = quad;
                for (Plane edge : cell.edges) {
                    clipped = clipVertices(clipped, edge);
                    if (clipped.isEmpty()) break;
                }
                if (clipped == quad) return List.of(quad);
                if (hasArea(clipped)) result.add(clipped);
            }
            return List.copyOf(result);
        }

        // 侧面投影可能恰好位于多个窗口的共享边；先合并线区间，避免侧面重复绘制。
        private List<List<Vertex>> clipSide(List<Vertex> quad, Line line) {
            List<Interval> intervals = new ArrayList<>();
            for (Cell cell : cells) {
                double low = line.low, high = line.high;
                for (Plane edge : cell.edges) {
                    double origin = edge.distance(line.x, line.y);
                    double slope = edge.nx * line.dx + edge.ny * line.dy;
                    if (Math.abs(slope) <= EPS) {
                        if (origin < -EPS) { high = low - 1; break; }
                    } else if (slope > 0) low = Math.max(low, -origin / slope);
                    else high = Math.min(high, -origin / slope);
                    if (low > high + EPS) break;
                }
                if (high - low > EPS && Double.isFinite(low) && Double.isFinite(high))
                    intervals.add(new Interval(low, high));
            }
            if (intervals.isEmpty()) return List.of();
            intervals.sort(Comparator.comparingDouble(Interval::low));
            List<Interval> merged = new ArrayList<>();
            double low = intervals.get(0).low, high = intervals.get(0).high;
            for (int i = 1; i < intervals.size(); i++) {
                Interval next = intervals.get(i);
                if (next.low <= high + EPS) high = Math.max(high, next.high);
                else { merged.add(new Interval(low, high)); low = next.low; high = next.high; }
            }
            merged.add(new Interval(low, high));
            List<List<Vertex>> result = new ArrayList<>();
            double origin = line.dx * line.x + line.dy * line.y;
            for (Interval interval : merged) {
                List<Vertex> clipped = clipVertices(quad, new Plane(line.dx, line.dy, origin + interval.low));
                clipped = clipVertices(clipped, new Plane(-line.dx, -line.dy, -origin - interval.high));
                if (hasArea(clipped)) result.add(clipped);
            }
            return List.copyOf(result);
        }
    }

    private record Interval(double low, double high) {}
    private record Line(double x, double y, double dx, double dy, double low, double high) {}
    private record Plane(double nx, double ny, double offset) {
        double distance(double x, double y) { return nx * x + ny * y - offset; }
        Plane inverse() { return new Plane(-nx, -ny, -offset); }
    }

    private static final class Cell {
        final List<Plane> edges;
        final double minX, minY, maxX, maxY;
        Cell(List<Point> polygon) {
            List<Plane> planes = new ArrayList<>();
            double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY;
            double x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < polygon.size(); i++) {
                Point a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
                double length = Math.hypot(b.x - a.x, b.y - a.y);
                double nx = (a.y - b.y) / length, ny = (b.x - a.x) / length;
                planes.add(new Plane(nx, ny, nx * a.x + ny * a.y));
                x0 = Math.min(x0, a.x); y0 = Math.min(y0, a.y);
                x1 = Math.max(x1, a.x); y1 = Math.max(y1, a.y);
            }
            edges = List.copyOf(planes);
            minX = x0; minY = y0; maxX = x1; maxY = y1;
        }
        boolean outside(List<Vertex> vertices) {
            boolean left = true, right = true, below = true, above = true;
            for (Vertex vertex : vertices) {
                left &= vertex.x < minX - EPS; right &= vertex.x > maxX + EPS;
                below &= vertex.y < minY - EPS; above &= vertex.y > maxY + EPS;
            }
            return left || right || below || above;
        }
    }

    private static List<List<Point>> subtract(List<Point> polygon, Cell cut) {
        List<List<Point>> outside = new ArrayList<>();
        List<Point> remainder = polygon;
        for (Plane edge : cut.edges) {
            List<Point> piece = clipPoints(remainder, edge.inverse());
            if (hasArea2(piece)) outside.add(piece);
            remainder = clipPoints(remainder, edge);
            if (!hasArea2(remainder)) break;
        }
        return outside;
    }

    private static List<Point> normalize(List<Point> input) {
        if (input == null) throw new IllegalArgumentException("Aperture region cannot be null");
        for (Point point : input) if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y))
            throw new IllegalArgumentException("Aperture points must be finite");
        List<Point> polygon = cleanPoints(input);
        if (!hasArea2(polygon)) throw new IllegalArgumentException("Aperture region has no area");
        double sign = Math.signum(area2(polygon));
        for (int i = 0; i < polygon.size(); i++) {
            Point a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            for (Point point : polygon) if (cross(a, b, point) * sign < -EPS)
                throw new IllegalArgumentException("Aperture region must be convex");
        }
        if (sign < 0) { polygon = new ArrayList<>(polygon); Collections.reverse(polygon); }
        return List.copyOf(polygon);
    }

    private static List<Point> clipPoints(List<Point> polygon, Plane edge) {
        if (polygon.isEmpty()) return polygon;
        int inside = 0;
        for (Point point : polygon) if (edge.distance(point.x, point.y) >= -EPS) inside++;
        if (inside == polygon.size()) return polygon;
        if (inside == 0) return List.of();
        List<Point> output = new ArrayList<>();
        Point previous = polygon.get(polygon.size() - 1);
        double previousDistance = edge.distance(previous.x, previous.y);
        for (Point point : polygon) {
            double distance = edge.distance(point.x, point.y);
            boolean fromInside = previousDistance >= -EPS, toInside = distance >= -EPS;
            if (fromInside != toInside) {
                double t = fraction(previousDistance, distance);
                output.add(new Point(lerp(previous.x, point.x, t), lerp(previous.y, point.y, t)));
            }
            if (toInside) output.add(point);
            previous = point; previousDistance = distance;
        }
        return cleanPoints(output);
    }

    private static List<Vertex> clipVertices(List<Vertex> polygon, Plane edge) {
        if (polygon.isEmpty()) return polygon;
        int inside = 0;
        for (Vertex vertex : polygon) if (edge.distance(vertex.x, vertex.y) >= -EPS) inside++;
        if (inside == polygon.size()) return polygon;
        if (inside == 0) return List.of();
        List<Vertex> output = new ArrayList<>();
        Vertex previous = polygon.get(polygon.size() - 1);
        double previousDistance = edge.distance(previous.x, previous.y);
        for (Vertex vertex : polygon) {
            double distance = edge.distance(vertex.x, vertex.y);
            boolean fromInside = previousDistance >= -EPS, toInside = distance >= -EPS;
            if (fromInside != toInside) {
                double t = fraction(previousDistance, distance);
                Vertex intersection = new Vertex(lerp(previous.x, vertex.x, t), lerp(previous.y, vertex.y, t),
                        lerp(previous.z, vertex.z, t), lerp(previous.u, vertex.u, t), lerp(previous.v, vertex.v, t));
                if (!finite(intersection)) return List.of();
                output.add(intersection);
            }
            if (toInside) output.add(vertex);
            previous = vertex; previousDistance = distance;
        }
        return cleanVertices(output);
    }

    private static List<Point> cleanPoints(List<Point> polygon) {
        List<Point> result = new ArrayList<>();
        for (Point point : polygon)
            if (result.isEmpty() || !same(result.get(result.size() - 1), point)) result.add(point);
        if (result.size() > 1 && same(result.get(0), result.get(result.size() - 1))) result.remove(result.size() - 1);
        boolean changed = true;
        while (changed && result.size() > 3) {
            changed = false;
            for (int i = 0; i < result.size(); i++) {
                Point a = result.get((i + result.size() - 1) % result.size());
                Point b = result.get(i), c = result.get((i + 1) % result.size());
                if (Math.abs(cross(a, b, c)) <= EPS * (Math.hypot(b.x - a.x, b.y - a.y) + Math.hypot(c.x - b.x, c.y - b.y))
                        && (b.x - a.x) * (c.x - b.x) + (b.y - a.y) * (c.y - b.y) >= 0) {
                    result.remove(i); changed = true; break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<Vertex> cleanVertices(List<Vertex> polygon) {
        List<Vertex> result = new ArrayList<>();
        for (Vertex vertex : polygon)
            if (result.isEmpty() || !same(result.get(result.size() - 1), vertex)) result.add(vertex);
        if (result.size() > 1 && same(result.get(0), result.get(result.size() - 1))) result.remove(result.size() - 1);
        return List.copyOf(result);
    }

    private static Line projectedLine(List<Vertex> quad) {
        Vertex start = null, end = null;
        double longest = 0;
        for (Vertex a : quad) for (Vertex b : quad) {
            double distance = Math.hypot(b.x - a.x, b.y - a.y);
            if (distance > longest) { longest = distance; start = a; end = b; }
        }
        if (longest <= EPS) return null;
        double dx = (end.x - start.x) / longest, dy = (end.y - start.y) / longest;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (Vertex vertex : quad) {
            double x = vertex.x - start.x, y = vertex.y - start.y;
            if (Math.abs(dx * y - dy * x) > EPS) return null;
            double along = dx * x + dy * y;
            low = Math.min(low, along); high = Math.max(high, along);
        }
        return new Line(start.x, start.y, dx, dy, low, high);
    }

    private static boolean hasArea(List<Vertex> polygon) {
        if (polygon.size() < 3) return false;
        Vertex origin = polygon.get(0);
        double x = 0, y = 0, z = 0;
        for (int i = 1; i + 1 < polygon.size(); i++) {
            Vertex a = polygon.get(i), b = polygon.get(i + 1);
            double ax = a.x - origin.x, ay = a.y - origin.y, az = a.z - origin.z;
            double bx = b.x - origin.x, by = b.y - origin.y, bz = b.z - origin.z;
            x += ay * bz - az * by; y += az * bx - ax * bz; z += ax * by - ay * bx;
        }
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.hypot(Math.hypot(x, y), z) > EPS * EPS;
    }

    private static boolean hasArea2(List<Point> polygon) {
        double area = area2(polygon);
        return polygon.size() >= 3 && Double.isFinite(area) && Math.abs(area) > EPS * EPS;
    }
    private static double area2(List<Point> polygon) {
        if (polygon.size() < 3) return 0;
        Point origin = polygon.get(0);
        double area = 0;
        for (int i = 1; i + 1 < polygon.size(); i++) area += cross(origin, polygon.get(i), polygon.get(i + 1));
        return area;
    }
    private static double cross(Point a, Point b, Point c) { return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x); }
    private static boolean same(Point a, Point b) { return Math.hypot(b.x - a.x, b.y - a.y) <= EPS; }
    private static boolean same(Vertex a, Vertex b) { return Math.hypot(Math.hypot(b.x - a.x, b.y - a.y), b.z - a.z) <= EPS; }
    private static boolean finite(Vertex v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y)
            && Double.isFinite(v.z) && Double.isFinite(v.u) && Double.isFinite(v.v); }
    private static double fraction(double from, double to) { return Math.max(0, Math.min(1, from / (from - to))); }
    private static double lerp(double a, double b, double t) { return a * (1 - t) + b * t; }
}
