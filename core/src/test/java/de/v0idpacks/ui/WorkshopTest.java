package de.v0idpacks.ui;

import com.google.gson.JsonObject;
import de.v0idpacks.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopTest {
    @TempDir Path temp;
    private EditorSession session;
    static BufferedImage sword(int color) {
        BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=1;y<11;y++) for(int x=0;x<3;x++) image.setRGB(12-y+x,y,color);
        for(int i=0;i<6;i++) image.setRGB(2+i,8+i,0xffd6b169);
        for(int i=0;i<4;i++) image.setRGB(1+i,14-i,0xff735c48);
        return image;
    }
    @AfterEach void close() { if(session!=null) session.close(); }
    private void prepare() throws Exception {
        new UiPreferences(temp.resolve("config")).setAnimationsEnabled(false);
        Path packs = temp.resolve("resourcepacks");
        String[] names={"diamond_sword","iron_sword","golden_sword","netherite_sword","diamond_axe","emerald","diamond_pickaxe","iron_axe","amethyst_shard"};
        int[] colors={0xff4ddbd2,0xffdbdfe3,0xffffcf61,0xff77717e,0xff5bc0cf,0xff76c998,0xff5faabd,0xffa8bed0,0xffb48fdb};
        for(int p=0;p<2;p++) {
            Path pack=packs.resolve(p==0?"§b§lOcean§r 16x (Demo)":"&6Ember 16x (Demo)");
            Files.createDirectories(pack.resolve("assets/minecraft/textures/item"));Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":46,\"description\":\"§7Generierte §bTesttexturen\"}}");
            BufferedImage icon=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
            var g=icon.createGraphics();g.setColor(new java.awt.Color(p==0?0x085569:0x743727));g.fillRect(0,0,32,32);g.drawImage(sword(p==0?0xff4ddbd2:0xffdf9a6f),4,4,24,24,null);g.dispose();
            ImageIO.write(icon,"png",pack.resolve("pack.png").toFile());
            for(int i=0;i<names.length;i++) ImageIO.write(sword(p==0?colors[i]:0xffdf9a6f),"png",pack.resolve("assets/minecraft/textures/item/"+names[i]+".png").toFile());
        }
        JsonObject metadata=new JsonObject();metadata.addProperty("pack_format",46);
        session=new EditorSession(packs,temp.resolve("config"),metadata,path->session.activationFinished(null));await();
    }
    private void await() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{do {Thread.sleep(10);session.poll();}while(session.busy());});
    }
    @Test void browserSearchFavoritesSelectionAndExportSurviveReopening() throws Exception {
        prepare();assertEquals(18,session.visible().size());
        EditorCanvas canvas=new EditorCanvas(session,()->{});canvas.draw();
        assertTrue(canvas.showingPacks());canvas.click(250,220,0);canvas.draw();
        assertFalse(canvas.showingPacks());assertFalse(session.packFilter().isEmpty());assertEquals(9,session.visible().size());
        canvas.click(250,120,0);canvas.draw();assertTrue(canvas.showingPacks());
        canvas.click(250,120,0);canvas.draw();assertFalse(canvas.showingPacks());
        canvas.click(250,80,0);for(char c:"diamond sword ocean".toCharArray()) canvas.type(c);
        assertEquals(1,session.visible().size());canvas.draw();canvas.click(250,230,0);
        assertNotNull(session.focused());session.favorite();await();session.select();await();
        assertEquals(1,session.favoriteCount());assertEquals(1,session.selectedCount());
        session.export(true);await();
        assertTrue(session.status().contains("aktiv"));
        session.filter(EditorSession.Tab.FAVORITES,"","");assertEquals(1,session.visible().size());
        session.filter(EditorSession.Tab.SELECTED,"","");assertEquals(1,session.visible().size());
        LibraryStore reopened=new LibraryStore(temp.resolve("config"));assertEquals(1,reopened.state().selected().size());assertEquals(1,reopened.state().favorites().size());
    }
    @Test void rendersRealEditorStatesAndOnlyUpdatesOnChange() throws Exception {
        prepare();EditorCanvas canvas=new EditorCanvas(session,()->{});
        Path dir=Path.of("build/previews");Files.createDirectories(dir);
        ImageIO.write(canvas.draw(),"png",dir.resolve("library.png").toFile());
        TextureRef selected=session.visible().stream().filter(t->t.name().equals("diamond_sword")&&t.pack().contains("Ocean")).findFirst().orElseThrow();
        session.filter(EditorSession.Tab.ALL,"",selected.pack());
        session.focus(selected);session.favorite();await();session.select();await();
        for(int i=0;i<80;i++){canvas.draw();Thread.sleep(10);session.poll();}
        canvas.mouseMove(200,200);BufferedImage image=canvas.draw();ImageIO.write(image,"png",dir.resolve("workshop.png").toFile());
        assertNotNull(session.preview());assertEquals(1120,image.getWidth());assertNull(canvas.draw());
        session.filter(EditorSession.Tab.SELECTED,"","");ImageIO.write(canvas.draw(),"png",dir.resolve("selected.png").toFile());
        session.filter(EditorSession.Tab.ALL,"no-match","");ImageIO.write(canvas.draw(),"png",dir.resolve("empty-search.png").toFile());
        session.filter(EditorSession.Tab.SELECTED,"","");canvas.resize(800,480);ImageIO.write(canvas.draw(),"png",dir.resolve("small-window.png").toFile());
    }
    @Test void previewHasTransparentSilhouetteAndChangesWhenRotated() {
        PreviewRenderer renderer=new PreviewRenderer();BufferedImage source=sword(0xff3dc5c0);
        BufferedImage front=renderer.render(source,PreviewRenderer.Mode.ITEM,240,240,0,0,1);
        BufferedImage rotated=renderer.render(source,PreviewRenderer.Mode.ITEM,240,240,1.1,.35,1);
        int filled=0,different=0;
        for(int y=0;y<240;y++) for(int x=0;x<240;x++) {if((front.getRGB(x,y)>>>24)>0)filled++;if(front.getRGB(x,y)!=rotated.getRGB(x,y))different++;}
        assertTrue(filled>1000 && filled<240*240/2);assertTrue(different>1000);assertEquals(0,front.getRGB(0,0));
    }
    @Test void globalCategoriesClearPackScopeAndCombineWithSearchAndRefresh() throws Exception {
        prepare();session.filter(EditorSession.Tab.ALL,"ocean",session.packs().getFirst().id());
        session.browseCategory(TextureCategory.SWORDS);
        assertEquals(EditorSession.Tab.ALL,session.tab());assertEquals("",session.packFilter());assertEquals("",session.query());
        assertEquals(8,session.visible().size());assertEquals(2,session.visible().stream().map(TextureRef::pack).distinct().count());
        session.search("diamond");assertEquals(2,session.visible().size());assertEquals(TextureCategory.SWORDS,session.categoryFilter());
        session.refresh();await();assertEquals(2,session.visible().size());assertEquals(TextureCategory.SWORDS,session.categoryFilter());
        session.browseCategory(TextureCategory.TOOLS);assertEquals(6,session.visible().size());assertEquals("",session.query());
        session.browseCategory(TextureCategory.ARMOR);assertTrue(session.visible().isEmpty());
        session.browseCategory(TextureCategory.ALL);assertEquals(18,session.visible().size());
        session.browseCategory(TextureCategory.SWORDS);session.filter(EditorSession.Tab.FAVORITES,"","");assertEquals(TextureCategory.ALL,session.categoryFilter());
        session.filter(EditorSession.Tab.ALL,"",session.packs().getFirst().id());assertEquals(9,session.visible().size());
    }
    @Test void categoryMenuSupportsMouseKeyboardAndDoesNotActivateCoveredControls() throws Exception {
        prepare();int[] closed={0};EditorCanvas canvas=new EditorCanvas(session,()->closed[0]++);canvas.draw();
        canvas.click(250,220,0);canvas.draw();assertEquals(9,session.visible().size());
        canvas.click(250,160,0);canvas.draw();
        canvas.key(70,false,false,"",s->{});canvas.key(256,false,false,"",s->{});canvas.draw();assertEquals(0,closed[0]);assertEquals(0,session.favoriteCount());
        canvas.click(250,160,0);canvas.draw();canvas.click(250,250,0);canvas.draw(); // Schwerter, second row, left.
        assertEquals(TextureCategory.SWORDS,session.categoryFilter());assertEquals(8,session.visible().size());assertFalse(canvas.showingPacks());
        canvas.click(250,80,0);for(char c:"diamond".toCharArray())canvas.type(c);canvas.draw();assertEquals(2,session.visible().size());
        canvas.click(250,220,0);assertNotNull(session.focused());canvas.draw();
        canvas.click(250,160,0);canvas.draw();canvas.key(32,false,false,"",s->{});canvas.draw();assertEquals(0,session.selectedCount());
        canvas.click(250,160,0);canvas.draw();canvas.key(262,false,false,"",s->{});canvas.key(257,false,false,"",s->{});canvas.draw();
        assertEquals(TextureCategory.ARMOR,session.categoryFilter());assertTrue(session.visible().isEmpty());
        canvas.click(250,160,0);canvas.draw();canvas.click(30,92,0);canvas.draw(); // First outside click only closes popup.
        assertEquals(TextureCategory.ARMOR,session.categoryFilter());canvas.click(30,92,0);canvas.draw();assertTrue(canvas.showingPacks());
    }
    @Test void rendersGlobalResultsAndCompactCategoryMenu() throws Exception {
        prepare();EditorCanvas canvas=new EditorCanvas(session,()->{});session.browseCategory(TextureCategory.SWORDS);
        for(int i=0;i<50;i++){canvas.draw();Thread.sleep(10);session.poll();}
        Path dir=Path.of("build/previews");Files.createDirectories(dir);
        canvas.mouseMove(200,200);ImageIO.write(canvas.draw(),"png",dir.resolve("categories-swords.png").toFile());
        canvas.click(250,160,0);ImageIO.write(canvas.draw(),"png",dir.resolve("categories-menu.png").toFile());
        canvas.resize(760,440);ImageIO.write(canvas.draw(),"png",dir.resolve("categories-small.png").toFile());
        canvas.click(370,220,0);BufferedImage empty=canvas.draw();assertEquals(TextureCategory.BLOCKS,session.categoryFilter()); // Right column at minimum width.
        ImageIO.write(empty,"png",dir.resolve("categories-empty.png").toFile());
    }
}
