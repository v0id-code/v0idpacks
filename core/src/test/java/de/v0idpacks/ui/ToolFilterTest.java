package de.v0idpacks.ui;

import com.google.gson.JsonObject;
import de.v0idpacks.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolFilterTest {
    @TempDir Path temp;
    private EditorSession session;
    @AfterEach void close(){if(session!=null)session.close();}
    private static byte[] icon(String type,int color) throws IOException {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(0xff8b6844,true));g.fillRect(7,4,2,10);g.setColor(new Color(color,true));
        switch(type) {
            case "pickaxe"->{g.fillRect(3,2,10,3);g.fillRect(2,4,3,3);g.fillRect(11,4,3,3);}
            case "axe"->{g.fillRect(6,2,7,6);g.fillRect(8,7,4,2);}
            case "shovel"->g.fillRect(5,1,6,6);
            case "hoe"->{g.fillRect(3,2,9,3);g.fillRect(2,4,3,3);}
            default->g.fillRect(3,3,10,10);
        }
        g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    private void prepare() throws Exception {
        new UiPreferences(temp.resolve("config")).setAnimationsEnabled(false);
        Path packs=temp.resolve("resourcepacks");Files.createDirectories(packs);
        for(int p=0;p<2;p++) {
            Map<String,byte[]> entries=new LinkedHashMap<>();
            entries.put("pack.mcmeta","{\"pack\":{\"pack_format\":46,\"description\":\"Generated tool fixtures\"}}".getBytes(StandardCharsets.UTF_8));
            for(String material:new String[]{"diamond","iron","netherite"})for(String type:new String[]{"pickaxe","axe","shovel","hoe"})
                entries.put("assets/minecraft/textures/item/"+material+"_"+type+".png",icon(type,p==0?0xff4ddbd2:0xffdf9a6f));
            for(String path:new String[]{"item/shears","item/fishing_rod","item/compass","item/diamond_sword","item/diamond_helmet","block/stone"})
                entries.put("assets/minecraft/textures/"+path+".png",icon("other",0xffa8bed0));
            if(p==0) {
                Path pack=packs.resolve("§bOcean Tools (Demo)");
                for(var e:entries.entrySet()){Path file=pack.resolve(e.getKey());Files.createDirectories(file.getParent());Files.write(file,e.getValue());}
            } else try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(packs.resolve("§6Ember Tools (Demo).zip")))) {
                for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
            }
        }
        JsonObject meta=new JsonObject();meta.addProperty("pack_format",46);session=new EditorSession(packs,temp.resolve("config"),meta,p->{});await();
    }
    private void await() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{do{Thread.sleep(10);session.poll();}while(session.busy());});
    }
    @Test void comparesToolVariantsAcrossFolderAndZipPacksAndResetsBothSubtypeFilters() throws Exception {
        prepare();session.filter(EditorSession.Tab.ALL,"ocean",session.packs().getFirst().id());
        session.browseToolType(ToolType.PICKAXE);assertEquals(TextureCategory.TOOLS,session.categoryFilter());assertEquals("",session.packFilter());assertEquals("",session.query());
        assertEquals(6,session.visible().size());assertEquals(2,session.visible().stream().map(TextureRef::pack).distinct().count());
        session.search("diamond");assertEquals(2,session.visible().size());
        for(ToolType type:new ToolType[]{ToolType.AXE,ToolType.SHOVEL,ToolType.HOE}) {
            session.browseToolType(type);assertEquals("diamond",session.query());assertEquals(2,session.visible().size());assertTrue(session.visible().stream().allMatch(type::matches));
        }
        session.refresh();await();assertEquals(ToolType.HOE,session.toolTypeFilter());assertEquals(2,session.visible().size());
        session.browseToolType(ToolType.ALL);assertEquals("diamond",session.query());assertEquals(8,session.visible().size());
        session.search("");assertEquals(30,session.visible().size());assertTrue(session.visible().stream().anyMatch(t->t.name().equals("shears")));
        session.browseToolType(ToolType.PICKAXE);session.browseArmorPart(ArmorPart.HELMET);assertEquals(ToolType.ALL,session.toolTypeFilter());assertEquals(2,session.visible().size());
        session.browseToolType(ToolType.AXE);assertEquals(ArmorPart.ALL,session.armorPartFilter());assertEquals(6,session.visible().size());
        session.browseCategory(TextureCategory.TOOLS);assertEquals(ToolType.ALL,session.toolTypeFilter());assertEquals(30,session.visible().size());
        session.browseToolType(ToolType.HOE);session.filter(EditorSession.Tab.FAVORITES,"","");assertEquals(ToolType.ALL,session.toolTypeFilter());
        session.filter(EditorSession.Tab.ALL,"",session.packs().getFirst().id());assertEquals(18,session.visible().size());
    }
    @Test void toolButtonsSupportMouseKeyboardAndCategoryPopupStillCoversThem() throws Exception {
        prepare();EditorCanvas canvas=new EditorCanvas(session,()->{});canvas.draw();
        canvas.click(250,160,0);canvas.draw();canvas.click(450,280,0);canvas.draw();assertEquals(TextureCategory.TOOLS,session.categoryFilter());
        canvas.click(340,195,0);canvas.draw();assertEquals(ToolType.PICKAXE,session.toolTypeFilter());assertEquals(6,session.visible().size());
        canvas.click(250,80,0);for(char c:"diamond".toCharArray())canvas.type(c);canvas.draw();assertEquals(2,session.visible().size());
        canvas.click(340,195,0);canvas.draw();canvas.key(258,false,false,"",s->{});canvas.draw();canvas.key(257,false,false,"",s->{});canvas.draw();
        assertEquals(ToolType.AXE,session.toolTypeFilter());assertEquals("diamond",session.query());assertEquals(2,session.visible().size());
        canvas.click(610,195,0);canvas.draw();assertEquals(ToolType.SHOVEL,session.toolTypeFilter());
        canvas.click(750,195,0);canvas.draw();assertEquals(ToolType.HOE,session.toolTypeFilter());
        canvas.click(250,160,0);canvas.draw();canvas.click(340,195,0);canvas.draw();assertEquals(ToolType.HOE,session.toolTypeFilter());
        canvas.click(200,195,0);canvas.draw();assertEquals(ToolType.ALL,session.toolTypeFilter());assertEquals(8,session.visible().size());
        canvas.click(250,120,0);canvas.draw();assertTrue(canvas.showingPacks());assertEquals(ToolType.ALL,session.toolTypeFilter());
    }
    @Test void toolComparisonAndEveryButtonFitTheSmallestSupportedCanvas() throws Exception {
        prepare();session.browseToolType(ToolType.PICKAXE);session.search("diamond");EditorCanvas canvas=new EditorCanvas(session,()->{});
        for(int i=0;i<40;i++){canvas.draw();Thread.sleep(10);session.poll();}
        Path out=Path.of("build/previews");Files.createDirectories(out);canvas.mouseMove(200,240);
        ImageIO.write(canvas.draw(),"png",out.resolve("tools-pickaxes.png").toFile());
        canvas.resize(760,440);ImageIO.write(canvas.draw(),"png",out.resolve("tools-small.png").toFile());
        // Each filter must remain reachable inside the 292-pixel library column.
        int[] centers={178,237,298,355,421};ToolType[] types=ToolType.values();
        for(int i=0;i<centers.length;i++){canvas.click(centers[i],195,0);canvas.draw();assertEquals(types[i],session.toolTypeFilter());}
        session.search("no-such-tool");ImageIO.write(canvas.draw(),"png",out.resolve("tools-empty.png").toFile());assertTrue(session.visible().isEmpty());
    }
}
