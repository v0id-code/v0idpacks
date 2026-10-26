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
import static de.v0idpacks.ui.CanvasControls.clickControl;

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
    @Test void invalidPanoramaShowsExportErrorAndNeverActivates() throws Exception {
        Path packs = temp.resolve("resourcepacks");
        String face = "assets/minecraft/textures/gui/title/background/panorama_0.png";
        Path source = packs.resolve("Panorama");
        Files.createDirectories(source.resolve(face).getParent());
        Files.writeString(source.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Test\"}}");
        ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB), "png", source.resolve(face).toFile());
        new LibraryStore(temp.resolve("config")).select(new TextureRef("Panorama",face,""),new PackCatalog(packs));
        int[] activations = {0};
        JsonObject metadata = new JsonObject(); metadata.addProperty("pack_format",46);
        session = new EditorSession(packs,temp.resolve("config"),metadata,path -> {
            activations[0]++;
            session.activationFinished(null);
        });
        await();
        for (boolean apply : new boolean[]{false,true}) {
            session.export(apply); await();
            assertTrue(session.error());
            assertTrue(session.status().contains("panorama_1.png fehlt"));
            assertFalse(session.busy());
            assertEquals(1,session.selectedCount());
        }
        assertEquals(0,activations[0]);
        try (var files = Files.list(packs)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".zip") || path.toString().endsWith(".tmp")));
        }
    }
    @Test void browserSearchFavoritesSelectionAndExportSurviveReopening() throws Exception {
        prepare();assertEquals(18,session.visible().size());
        EditorCanvas canvas=new EditorCanvas(session,()->{});canvas.draw();
        assertTrue(canvas.showingPacks());clickControl(canvas,canvas.firstControl("source:"));canvas.draw();
        assertFalse(canvas.showingPacks());assertFalse(session.packFilter().isEmpty());assertEquals(9,session.visible().size());
        clickControl(canvas,"pack");canvas.draw();assertTrue(canvas.showingPacks());
        clickControl(canvas,"pack");canvas.draw();assertFalse(canvas.showingPacks());
        clickControl(canvas,"search");for(char c:"diamond sword ocean".toCharArray()) canvas.type(c);
        assertEquals(1,session.visible().size());canvas.draw();clickControl(canvas,canvas.firstControl("tile:"));
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
    @Test void identityDialogSavesNameAndLogoBeforeExportAndSurvivesReopening() throws Exception {
        prepare(); session.focus(session.visible().getFirst()); session.select(); await();
        Path logo = temp.resolve("my-logo.png"); ImageIO.write(sword(0xff29addd), "png", logo.toFile());
        int[] choices = {0};
        EditorCanvas canvas = new EditorCanvas(session, () -> {}, () -> { choices[0]++; session.importLogo(logo); });
        canvas.draw(); clickControl(canvas,"identity");clickControl(canvas,"logo");await();canvas.draw();
        assertEquals(1, choices[0]); assertTrue(session.hasLogo()); assertNotNull(session.logo());
        clickControl(canvas,"name"); canvas.key(65,false,true,"",s -> {});
        for (char c : "Mein Über-Pack".toCharArray()) canvas.type(c);
        clickControl(canvas,"identity-close");await();canvas.draw();clickControl(canvas,"export");await();
        assertEquals("Mein Über-Pack", session.name()); assertFalse(session.error());
        clickControl(canvas,"identity");clickControl(canvas,"name");
        assertTrue(canvas.narration().contains("Pack-Name"));
        Path dir=Path.of("build/previews"); Files.createDirectories(dir);
        ImageIO.write(canvas.draw(), "png", dir.resolve("pack-identity.png").toFile());
        canvas.resize(760,440); ImageIO.write(canvas.draw(), "png", dir.resolve("pack-identity-small.png").toFile());
        session.close(); Files.delete(logo);
        JsonObject metadata = new JsonObject(); metadata.addProperty("pack_format",46);
        session = new EditorSession(temp.resolve("resourcepacks"),temp.resolve("config"),metadata,path -> {}); await();
        assertEquals("Mein Über-Pack", session.name()); assertNotNull(session.logo());
        session.removeLogo(); await(); assertNull(session.logo()); assertFalse(session.hasLogo());
    }
    @Test void invalidIdentityStaysOpenAndCannotTriggerExportBehindDialog() throws Exception {
        prepare(); session.focus(session.visible().getFirst()); session.select(); await();
        EditorCanvas canvas = new EditorCanvas(session, () -> {}); canvas.draw();
        var export=canvas.controlBounds("export");
        clickControl(canvas,"identity");clickControl(canvas,"name");canvas.key(65,false,true,"",s -> {});canvas.key(259,false,false,"",s -> {});
        clickControl(canvas,"identity-close");await();canvas.draw();
        assertTrue(session.error()); assertTrue(session.status().contains("Pack-Namen"));
        assertDoesNotThrow(()->canvas.controlBounds("name"));
        canvas.click(export.x+export.width/2,export.y+export.height/2,0);
        canvas.key(70,false,false,"",s->{});canvas.key(32,false,false,"",s->{});
        assertEquals(0,session.favoriteCount());assertEquals(1,session.selectedCount());
        try (var files = Files.list(temp.resolve("resourcepacks"))) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".zip"))); }
    }
    @Test void projectManagerCreatesSwitchesAndPreservesNameDraft() throws Exception {
        prepare(); session.focus(session.visible().getFirst());session.select();await();
        String first=session.projectId();
        EditorCanvas canvas=new EditorCanvas(session,()->{});canvas.draw();
        clickControl(canvas,"identity");clickControl(canvas,"name");canvas.key(65,false,true,"",s->{});
        for(char c:"Mein erstes Pack".toCharArray())canvas.type(c);
        clickControl(canvas,"identity-close");await();canvas.draw();clickControl(canvas,"projects");canvas.draw();
        // New pack has keyboard focus; the modal blocks the global selection shortcut.
        canvas.key(257,false,false,"",s->{});await();canvas.draw();
        String second=session.projectId();assertNotEquals(first,second);assertEquals(0,session.selectedCount());
        assertEquals(EditorSession.Tab.SELECTED,session.tab());assertNull(session.focused());
        assertEquals("Mein erstes Pack",session.projects().get(first).name());
        clickControl(canvas,"projects");canvas.draw();canvas.click(420,200,0);await();canvas.draw();
        assertEquals(first,session.projectId());assertEquals("Mein erstes Pack",session.name());assertEquals(1,session.selectedCount());
        assertEquals("Mein v0idpack 2",session.projects().get(second).name());
        session.importLogo(temp.resolve("missing.png"),second);assertTrue(session.error());assertFalse(session.hasLogo());
        clickControl(canvas,"projects");canvas.draw();
        Path dir=Path.of("build/previews");Files.createDirectories(dir);
        canvas.resize(760,440);ImageIO.write(canvas.draw(),"png",dir.resolve("project-manager-small.png").toFile());
        canvas.key(256,false,false,"",s->{});canvas.draw();
        session.createProject(session.name());await();canvas.draw();
        assertEquals(3,session.projects().size());
        LibraryStore reopened=new LibraryStore(temp.resolve("config"));assertEquals(session.projectId(),reopened.projectId());
        assertEquals("Mein erstes Pack",reopened.projects().get(first).name());
    }
    @Test void identityKeyboardStaysInDialogAndEscapeSavesWithoutClosingEditor() throws Exception {
        prepare();session.focus(session.visible().getFirst());await();
        int[] closed={0};EditorCanvas canvas=new EditorCanvas(session,()->closed[0]++);canvas.resize(760,440);canvas.draw();
        clickControl(canvas,"identity");canvas.draw();
        assertFalse(canvas.drag(20,20));assertTrue(canvas.scroll(0,0,1));
        canvas.key(65,false,true,"",s->{});for(char c:"Überblick".toCharArray())canvas.type(c);
        canvas.key(258,false,false,"",s->{});canvas.draw();assertTrue(canvas.narration().contains("PNG-Logo wählen"));
        canvas.key(70,false,false,"",s->{});canvas.key(32,false,false,"",s->{});await();
        assertEquals(0,session.favoriteCount());assertEquals(0,session.selectedCount());
        canvas.key(258,true,false,"",s->{});canvas.draw();assertTrue(canvas.narration().contains("Pack-Name"));
        canvas.key(256,false,false,"",s->{});await();canvas.draw();
        assertEquals("Überblick",session.name());assertEquals(0,closed[0]);
        assertDoesNotThrow(()->canvas.controlBounds("identity"));
        assertThrows(NoSuchElementException.class,()->canvas.controlBounds("name"));
        clickControl(canvas,"identity");canvas.draw();canvas.key(65,false,true,"",s->{});
        for(char c:"Enter speichert".toCharArray())canvas.type(c);
        canvas.key(257,false,false,"",s->{});await();canvas.draw();assertEquals("Enter speichert",session.name());
        canvas.key(256,false,false,"",s->{});assertEquals(1,closed[0]);
    }
    @Test void wideLibraryOpensAnIsolatedPreviewAndReturnsToTheSameFilteredGrid() throws Exception {
        prepare();session.browseCategory(TextureCategory.SWORDS);session.search("diamond");
        int[] closed={0};EditorCanvas canvas=new EditorCanvas(session,()->closed[0]++);canvas.resize(760,440);canvas.draw();
        assertThrows(NoSuchElementException.class,()->canvas.controlBounds("viewport"));
        assertTrue(canvas.controlBounds("search").x<40);
        clickControl(canvas,"refresh");await();canvas.draw();assertTrue(session.status().contains("Packs"));
        String tile=canvas.firstControl("tile:");
        var before=canvas.controlBounds(tile);clickControl(canvas,tile);canvas.draw();
        assertFalse(canvas.drag(20,20)); // A tile click must not start rotating the new preview.
        assertThrows(NoSuchElementException.class,()->canvas.controlBounds("refresh"));
        var viewport=canvas.controlBounds("viewport");assertTrue(viewport.width>300);assertTrue(viewport.height>250);
        // Empty dialog space consumes clicks; only the backdrop dismisses it.
        canvas.click(500,100,0);canvas.draw();assertDoesNotThrow(()->canvas.controlBounds("viewport"));
        clickControl(canvas,"viewport");assertTrue(canvas.drag(16,8));canvas.release();
        canvas.scroll(0,0,-1);assertEquals("diamond",session.query());assertEquals(TextureCategory.SWORDS,session.categoryFilter());
        clickControl(canvas,"favorite");await();assertEquals(1,session.favoriteCount());
        clickControl(canvas,"select");await();assertEquals(1,session.selectedCount());
        Path out=Path.of("build/previews");Files.createDirectories(out);
        for(int i=0;i<30;i++){canvas.draw();session.poll();Thread.sleep(10);}
        canvas.mouseMove(viewport.x+10,viewport.y+10);ImageIO.write(canvas.draw(),"png",out.resolve("texture-preview-small.png").toFile());
        canvas.resize(1120,660);ImageIO.write(canvas.draw(),"png",out.resolve("texture-preview.png").toFile());
        canvas.resize(760,440);canvas.draw();canvas.key(256,false,false,"",s->{});canvas.draw();
        assertEquals(0,closed[0]);assertEquals(before,canvas.controlBounds(tile));
        assertEquals("diamond",session.query());assertEquals(TextureCategory.SWORDS,session.categoryFilter());
        clickControl(canvas,tile);canvas.draw();clickControl(canvas,"preview-close");canvas.draw();
        assertThrows(NoSuchElementException.class,()->canvas.controlBounds("viewport"));
        clickControl(canvas,tile);canvas.draw();canvas.click(4,4,0);canvas.draw();
        assertDoesNotThrow(()->canvas.controlBounds("search"));
        canvas.key(256,false,false,"",s->{});assertEquals(1,closed[0]);
    }
    @Test void previewReturnsToTheSamePageAndSpaceOnBackNeverSelectsATexture() throws Exception {
        prepare();EditorCanvas canvas=new EditorCanvas(session,()->{});canvas.resize(760,440);canvas.draw();
        clickControl(canvas,"pack");canvas.draw();clickControl(canvas,"next");canvas.draw();
        String tile=canvas.firstControl("tile:");var position=canvas.controlBounds(tile);
        clickControl(canvas,tile);canvas.draw();clickControl(canvas,"viewport");canvas.key(258,true,false,"",s->{});canvas.draw();
        assertTrue(canvas.narration().contains("Zurück"));
        canvas.key(32,false,false,"",s->{});canvas.draw();assertEquals(0,session.selectedCount());
        assertEquals(position,canvas.controlBounds(tile));
        assertThrows(NoSuchElementException.class,()->canvas.controlBounds("viewport"));
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
        clickControl(canvas,canvas.firstControl("source:"));canvas.draw();assertEquals(9,session.visible().size());
        clickControl(canvas,"category");canvas.draw();
        canvas.key(70,false,false,"",s->{});canvas.key(256,false,false,"",s->{});canvas.draw();assertEquals(0,closed[0]);assertEquals(0,session.favoriteCount());
        clickControl(canvas,"category");canvas.draw();clickControl(canvas,"category-option:2");canvas.draw(); // Schwerter, second row, left.
        assertEquals(TextureCategory.SWORDS,session.categoryFilter());assertEquals(8,session.visible().size());assertFalse(canvas.showingPacks());
        clickControl(canvas,"search");for(char c:"diamond".toCharArray())canvas.type(c);canvas.draw();assertEquals(2,session.visible().size());
        clickControl(canvas,canvas.firstControl("tile:"));assertNotNull(session.focused());canvas.draw();
        clickControl(canvas,"preview-close");canvas.draw();
        clickControl(canvas,"category");canvas.draw();canvas.key(32,false,false,"",s->{});canvas.draw();assertEquals(0,session.selectedCount());
        clickControl(canvas,"category");canvas.draw();canvas.key(262,false,false,"",s->{});canvas.key(257,false,false,"",s->{});canvas.draw();
        assertEquals(TextureCategory.ARMOR,session.categoryFilter());assertTrue(session.visible().isEmpty());
        clickControl(canvas,"category");canvas.draw();canvas.click(200,28,0);canvas.draw(); // First outside click only closes popup.
        assertEquals(TextureCategory.ARMOR,session.categoryFilter());canvas.click(200,28,0);canvas.draw();assertTrue(canvas.showingPacks());
    }
    @Test void rendersGlobalResultsAndCompactCategoryMenu() throws Exception {
        prepare();EditorCanvas canvas=new EditorCanvas(session,()->{});session.browseCategory(TextureCategory.SWORDS);
        for(int i=0;i<50;i++){canvas.draw();Thread.sleep(10);session.poll();}
        Path dir=Path.of("build/previews");Files.createDirectories(dir);
        canvas.mouseMove(200,200);ImageIO.write(canvas.draw(),"png",dir.resolve("categories-swords.png").toFile());
        clickControl(canvas,"category");ImageIO.write(canvas.draw(),"png",dir.resolve("categories-menu.png").toFile());
        canvas.resize(760,440);ImageIO.write(canvas.draw(),"png",dir.resolve("categories-small.png").toFile());
        clickControl(canvas,"category-option:1");BufferedImage empty=canvas.draw();assertEquals(TextureCategory.BLOCKS,session.categoryFilter()); // Right column at minimum width.
        ImageIO.write(empty,"png",dir.resolve("categories-empty.png").toFile());
    }
}
