package top.elysianherd.magicaland.client.gui.ponycustom;

import java.util.ArrayDeque;
import java.util.Arrays;
import top.elysianherd.magicaland.cutiemark.CutieMarkData;

/** 一次完整笔画是一条历史；实例仅属于一个预设草稿。 */
public final class PixelCanvasHistory {
    public interface Target {
        int[] pixels(boolean left);
        void pixels(boolean left, int[] pixels);
        boolean linked();
        void linked(boolean linked, boolean sourceLeft);
    }
    public enum Tool { BRUSH, ERASER, PICKER }
    public static final int HISTORY_LIMIT = 64;
    private record State(int[] left, int[] right, boolean linked) {}
    private final Target target;
    private final ArrayDeque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private State stroke;
    private boolean left = true;
    private int previousX = -1, previousY = -1;
    private int color = 0xFFB35BCC;
    private Tool tool = Tool.BRUSH;

    public PixelCanvasHistory(Target target) { this.target = target; }
    public boolean left() { return left; }
    public boolean linked() { return target.linked(); }
    public int color() { return color; }
    public Tool tool() { return tool; }
    public boolean drawing() { return stroke != null; }
    public int[] pixels() { return target.pixels(left); }
    public boolean canUndo() { return !undo.isEmpty() || stroke != null && !same(stroke, snapshot()); }
    public boolean canRedo() { return stroke == null && !redo.isEmpty(); }
    public void color(int argb) { finishStroke(); color = 0xFF000000 | argb & 0xFFFFFF; }
    public void tool(Tool tool) { finishStroke(); this.tool = tool; }
    public void side(boolean left) { finishStroke(); this.left = left; }

    public void beginStroke(int x, int y) {
        finishStroke();
        if (!inside(x, y)) return;
        if (tool == Tool.PICKER) {
            int sample = pixels()[y * CutieMarkData.SIZE + x];
            if ((sample >>> 24) != 0) color = 0xFF000000 | sample & 0xFFFFFF;
            return;
        }
        stroke = snapshot();
        previousX = previousY = -1;
        continueStroke(x, y);
    }

    public void continueStroke(int x, int y) {
        if (stroke == null) return;
        if (!inside(x, y)) { previousX = previousY = -1; return; }
        int[] pixels = pixels();
        int fromX = previousX < 0 ? x : previousX, fromY = previousY < 0 ? y : previousY;
        int dx = Math.abs(x - fromX), sx = fromX < x ? 1 : -1;
        int dy = -Math.abs(y - fromY), sy = fromY < y ? 1 : -1, error = dx + dy;
        boolean changed = false;
        int ink = tool == Tool.ERASER ? 0 : color;
        while (true) {
            int index = fromY * CutieMarkData.SIZE + fromX;
            if (pixels[index] != ink) { pixels[index] = ink; changed = true; }
            if (fromX == x && fromY == y) break;
            int twice = error * 2;
            if (twice >= dy) { error += dy; fromX += sx; }
            if (twice <= dx) { error += dx; fromY += sy; }
        }
        previousX = x; previousY = y;
        if (changed) target.pixels(left, pixels);
    }

    public void finishStroke() {
        if (stroke != null && !same(stroke, snapshot())) remember(stroke);
        stroke = null;
        previousX = previousY = -1;
    }
    public void mirror() {
        finishStroke();
        State before = snapshot();
        target.pixels(left, CutieMarkData.mirror(pixels()));
        if (!same(before, snapshot())) remember(before);
    }
    public void setLinked(boolean linked) {
        finishStroke();
        if (linked == target.linked()) return;
        State before = snapshot();
        target.linked(linked, left);
        remember(before);
    }
    public void undo() {
        finishStroke();
        if (undo.isEmpty()) return;
        redo.addLast(snapshot());
        restore(undo.removeLast());
    }
    public void redo() {
        finishStroke();
        if (redo.isEmpty()) return;
        undo.addLast(snapshot());
        restore(redo.removeLast());
    }
    private State snapshot() { return new State(target.pixels(true).clone(), target.pixels(false).clone(), target.linked()); }
    private void restore(State state) {
        target.linked(false, true);
        target.pixels(true, state.left.clone());
        target.pixels(false, state.right.clone());
        target.linked(state.linked, true);
    }
    private void remember(State state) {
        undo.addLast(state);
        while (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear();
    }
    private static boolean same(State first, State second) {
        return first.linked == second.linked && Arrays.equals(first.left, second.left) && Arrays.equals(first.right, second.right);
    }
    private static boolean inside(int x, int y) { return x >= 0 && y >= 0 && x < CutieMarkData.SIZE && y < CutieMarkData.SIZE; }
}
