import top.elysianherd.magicaland.client.gui.ponycustom.PresetMenuLayout;

public final class PresetMenuLayoutTest {
    private static int checks;
    public static void main(String[] args) {
        for(int window=320;window<=1920;window+=8) {
            var preview=top.elysianherd.magicaland.client.gui.ponycustom.CustomizationLayout.of(8,31,window-16,201).preview();
            int width=preview.width()-10;
            var header=PresetMenuLayout.header(width);
            check(header.dropdownWidth()>=52,"narrow window still shows preset text and arrow");
            check(header.buttonWidth()>=18 && header.buttonWidth()<=20,"usable icon button width");
            check(header.createX()==header.dropdownWidth()+3,"dropdown and create gap");
            check(header.deleteX()==header.createX()+header.buttonWidth()+3,"create and delete gap");
            check(header.deleteX()+header.buttonWidth()==width,"shortcuts fit preview row exactly");
        }
        for(int screen: new int[]{240,270,360,480,720,1080}) for(int y=5;y<screen-25;y+=13) for(int count: new int[]{0,1,2,8,9,50,100}) {
            var menu=PresetMenuLayout.of(y,20,screen,count);
            check(menu.top()>=2 && menu.top()+menu.height()<=screen-2,"inside screen");
            check(menu.rows()>=0 && menu.rows()<=Math.min(8,count),"visible count bounded");
            check(menu.rowAt(menu.top()-.01)==-1 && menu.rowAt(menu.top()+menu.height())==-1,"half-open outer bounds");
            check(menu.rowAt(Double.NaN)==-1 && menu.rowAt(Double.POSITIVE_INFINITY)==-1,"finite input");
            for(int row=0;row<=menu.rows();row++) {
                check(menu.rowAt(menu.top()+row*20)==row,"row top ownership");
                check(menu.rowAt(menu.top()+(row+1)*20-.001)==row,"row bottom ownership");
            }
            check(menu.rowAt(menu.top()+menu.rows()*20+10)==menu.rows(),"management footer always available");
        }
        System.out.println("PASS PresetMenuLayoutTest: " + checks + " viewport, rows, management and hit-boundary checks");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
