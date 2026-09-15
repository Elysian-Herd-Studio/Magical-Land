import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.Gson;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.gui.ponycustom.ModelPage;
import top.elysianherd.magicaland.client.gui.ponycustom.PonyCustomPageContext;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;

public final class PresetShortcutTest {
    private static int checks, returns;
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory(Path.of(args[0]),"shortcut-test-");
        System.setProperty("magicaland.test.config",dir.toString());
        ModelManager.init(); check(ModelManager.createModel("旧预设"),"setup");
        var page=new ModelPage();
        check(!page.beginCreate(true) && !page.beginDelete(true),"shortcuts cannot write outside editor");
        check(ModelManager.beginEditing(),"begin isolated editor");
        Object original=ModelManager.getActiveModel();
        String applied=new Gson().toJson(ModelManager.getAppliedModel());
        var disk=snapshot(dir); int sends=ClientNetworkHandler.sends;
        var context=new PonyCustomPageContext(null,0,0,158,150,(target,direction)->{
            check(target==PonyCustomPageContext.Page.MAIN && direction==-1,"returns to prior category");returns++;
        },()->{});
        field(page,"context",context);
        check(page.beginCreate(true) && page.isEditingPreset(),"new opens naming dialog");
        check(!page.beginCreate(true) && !page.beginDelete(true),"pending dialog cannot be replaced");
        check(ModelManager.getActiveModel()==original && !ModelManager.isDirty(),"opening new does not create or switch");
        field(page,"draft","未创建"); check(page.keyPressed(256),"escape cancels creation");
        check(!page.isEditingPreset() && ModelManager.getActiveModel()==original && !ModelManager.isDirty(),"new cancel preserves selection and clean state");
        check(page.beginCreate(true),"reopen creation"); field(page,"draft","新预设");
        check(page.keyPressed(257),"enter confirms creation");
        check(ModelManager.getActiveModel().name.equals("新预设") && ModelManager.getActiveModel()!=original,"successful creation switches draft");
        check(ModelManager.isPresetDirty("新预设") && !Files.exists(dir.resolve("magicaland/ponies/新预设.json")),"new preset remains memory only");
        var fresh=ModelManager.getActiveModel();
        check(page.beginDelete(true),"delete opens confirmation");
        check("新预设".equals(field(page,"deleteTarget")),"confirmation captures exact current name");
        check(ModelManager.getActiveModel()==fresh && ModelManager.getAvailableModels().contains("新预设"),"opening delete does not remove");
        page.keyPressed(256);
        check(ModelManager.getActiveModel()==fresh && ModelManager.getAvailableModels().contains("新预设"),"delete cancel preserves current draft");
        check(page.beginDelete(true),"reopen delete"); page.keyPressed(257);
        check(!ModelManager.getAvailableModels().contains("新预设") && ModelManager.getActiveModel()!=fresh,"confirmed deletion selects surviving draft");
        check(!page.isEditingPreset() && returns==4,"every shortcut confirm/cancel returns and closes dialog");
        while(ModelManager.getAvailableModels().size()>1) check(ModelManager.deleteModel(ModelManager.getActiveModel().name),"reduce draft count");
        check(!page.beginDelete(true) && !page.isEditingPreset(),"last preset deletion cannot even open");
        check(snapshot(dir).equals(disk),"all shortcut operations remain off disk until save");
        check(applied.equals(new Gson().toJson(ModelManager.getAppliedModel())) && ClientNetworkHandler.sends==sends,"no applied or multiplayer mutation");
        ModelManager.discardEditing();
        check(snapshot(dir).equals(disk) && ModelManager.getAvailableModels().contains("旧预设"),"discard restores deleted presets");
        check(!page.beginCreate(true) && !page.beginDelete(true),"closed editor guarded");
        System.out.println("PASS PresetShortcutTest: "+checks+" naming, confirmation, cancellation, return and draft isolation checks");
    }
    private static void field(Object instance,String name,Object value)throws Exception {var f=ModelPage.class.getDeclaredField(name);f.setAccessible(true);f.set(instance,value);}
    private static Object field(Object instance,String name)throws Exception {var f=ModelPage.class.getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
    private static Map<String,String> snapshot(Path dir)throws Exception {var result=new TreeMap<String,String>();try(var paths=Files.walk(dir)){for(Path p:paths.filter(Files::isRegularFile).toList())result.put(dir.relativize(p).toString(),java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(p)));}return result;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
