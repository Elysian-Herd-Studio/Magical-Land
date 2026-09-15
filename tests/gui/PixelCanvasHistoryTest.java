import java.util.Arrays;
import top.elysianherd.magicaland.cutiemark.CutieMarkData;
import top.elysianherd.magicaland.client.gui.ponycustom.PixelCanvasHistory;

public final class PixelCanvasHistoryTest {
    private static int checks;
    private static final class Target implements PixelCanvasHistory.Target {
        int[] left = new int[144], right = new int[144]; boolean linked = true; int writes;
        public int[] pixels(boolean side) { return (linked || side ? left : right).clone(); }
        public void pixels(boolean side, int[] value) { writes++; if (linked || side) left = value.clone(); else right = value.clone(); }
        public boolean linked() { return linked; }
        public void linked(boolean value, boolean source) {
            if (value == linked) return;
            if (value) { left = pixels(source); right = new int[144]; } else right = left.clone();
            linked = value;
        }
    }
    public static void main(String[] args) {
        Target target = new Target(); var editor = new PixelCanvasHistory(target);
        check(!editor.canUndo() && !editor.canRedo(), "clean history");
        editor.color(0xFF123456); editor.beginStroke(0,0); editor.continueStroke(11,11); editor.finishStroke();
        for (int i=0;i<12;i++) check(target.left[i*12+i]==0xFF123456, "diagonal interpolation " + i);
        check(Arrays.equals(target.pixels(true),target.pixels(false)), "linked paints both");
        check(target.writes==2, "at most one target write per input event");
        editor.undo(); check(Arrays.stream(target.left).allMatch(v->v==0) && !editor.canUndo(), "full stroke one undo");
        editor.redo(); check(target.left[143]==0xFF123456, "redo full stroke");
        editor.beginStroke(0,0); editor.finishStroke(); editor.undo();
        check(Arrays.stream(target.left).allMatch(v->v==0), "no-op does not add undo");
        editor.tool(PixelCanvasHistory.Tool.PICKER); editor.beginStroke(5,5);
        check(editor.color()==0xFF123456 && editor.canRedo(), "transparent picker preserves ink and redo");
        editor.redo(); editor.color(0xFFABCDEF); editor.beginStroke(0,0);
        check(editor.color()==0xFF123456 && !editor.drawing(), "picker samples opaque without stroke");
        editor.tool(PixelCanvasHistory.Tool.ERASER); editor.beginStroke(0,0); editor.finishStroke();
        check(target.left[0]==0 && target.left[13]!=0, "eraser clears just selected pixel");
        editor.undo(); check(target.left[0]!=0, "undo erase");
        editor.setLinked(false); editor.side(false); editor.tool(PixelCanvasHistory.Tool.BRUSH);
        editor.color(0xFF765432); editor.beginStroke(2,0); editor.finishStroke();
        check(target.right[2]==0xFF765432 && target.left[2]==0, "unlock isolates sides");
        int[] beforeLeft=target.left.clone(), beforeRight=target.right.clone();
        editor.setLinked(true);
        check(target.linked && Arrays.equals(target.left,beforeRight), "relink selects current right side");
        editor.undo(); check(!target.linked && Arrays.equals(target.left,beforeLeft) && Arrays.equals(target.right,beforeRight), "undo relink restores both sides");
        editor.redo(); check(target.linked && Arrays.equals(target.left,beforeRight), "redo relink");
        editor.mirror(); check(Arrays.equals(target.left,CutieMarkData.mirror(beforeRight)), "mirror selected shared image");
        editor.undo(); check(Arrays.equals(target.left,beforeRight), "undo mirror");
        Target second = new Target(); var other = new PixelCanvasHistory(second);
        other.beginStroke(0,0); other.continueStroke(-1,-1); other.continueStroke(11,0); other.finishStroke();
        check(second.left[0]!=0 && second.left[11]!=0, "reentry endpoints painted");
        for(int x=1;x<11;x++) check(second.left[x]==0, "outside canvas never bridges " + x);
        check(Arrays.equals(target.left,beforeRight), "separate preset history isolated");
        other.undo(); other.color(0x112233); other.beginStroke(1,1); other.finishStroke();
        check(!other.canRedo() && second.left[13]==0xFF112233, "new edit clears redo and forces opaque ink");
        for(int i=0;i<80;i++) {other.color(i);other.beginStroke(0,0);other.finishStroke();}
        int undos=0; while(other.canUndo()){other.undo();undos++;}
        check(undos==PixelCanvasHistory.HISTORY_LIMIT, "bounded history");
        int redos=0; while(other.canRedo()){other.redo();redos++;}
        check(redos==PixelCanvasHistory.HISTORY_LIMIT, "bounded redo retains exact history");
        for(int x=0;x<12;x++) for(int y=0;y<12;y++) for(int ex=0;ex<12;ex++) {
            Target t=new Target(); var e=new PixelCanvasHistory(t);
            e.beginStroke(x,y); e.continueStroke(ex,11-y); e.finishStroke();
            check(t.left[y*12+x]!=0 && t.left[(11-y)*12+ex]!=0, "all line endpoints survive");
            check(Arrays.stream(t.left).filter(v->v!=0).count()<=12, "line stays bounded");
            e.undo(); check(Arrays.stream(t.left).allMatch(v->v==0), "line exact undo");
        }
        System.out.println("PASS PixelCanvasHistoryTest: " + checks + " stroke, tools, linked sides, history and isolation checks");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
