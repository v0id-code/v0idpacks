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

class ArmorFilterTest {
    @TempDir Path temp;
    private EditorSession session;
    @AfterEach void close(){if(session!=null)session.close();}
    private static byte[] icon(String part,int color) throws IOException {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(new Color(color,true));
        switch(part) {
            case "helmet"->{g.fillRect(3,3,10,5);g.fillRect(2,6,3,5);g.fillRect(11,6,3,5);}
            case "chestplate"->{g.fillRect(1,3,4,5);g.fillRect(11,3,4,5);g.fillRect(4,4,8,10);}
            case "leggings"->{g.fillRect(3,2,10,4);g.fillRect(3,6,4,8);g.fillRect(9,6,4,8);}
            case "boots"->{g.fillRect(3,5,4,7);g.fillRect(9,5,4,7);g.fillRect(1,10,6,4);g.fillRect(9,10,6,4);}
            default->g.fillRect(3,3,10,10);
        }
        g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    private void prepare() throws Exception {
        new UiPreferences(temp.resolve("config")).setAnimationsEnabled(false);
        Path packs=temp.resolve("resourcepacks");Files.createDirectories(packs);
        for(int p=0;p<2;p++) {
            Map<String,byte[]> entries=new LinkedHashMap<>();
            entries.put("pack.mcmeta","{\"pack\":{\"pack_format\":46,\"description\":\"Generated armor fixtures\"}}".getBytes(StandardCharsets.UTF_8));
            for(String material:new String[]{"diamond","iron","leather"})for(String part:new String[]{"helmet","chestplate","leggings","boots"})
                entries.put("assets/minecraft/textures/item/"+material+"_"+part+".png",icon(part,p==0?0xff4ddbd2:0xffdf9a6f));
            for(String path:new String[]{"item/leather_helmet_overlay","item/diamond_horse_armor","entity/equipment/humanoid/diamond","entity/equipment/humanoid_leggings/diamond","item/iron_sword","block/stone"})
                entries.put("assets/minecraft/textures/"+path+".png",icon("other",0xffa8bed0));
            if(p==0) {
                Path pack=packs.resolve("§bOcean Armor (Demo)");
                for(var e:entries.entrySet()){Path file=pack.resolve(e.getKey());Files.createDirectories(file.getParent());Files.write(file,e.getValue());}
            } else try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(packs.resolve("§6Ember Armor (Demo).zip")))) {
                for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
            }
        }
        JsonObject meta=new JsonObject();meta.addProperty("pack_format",46);session=new EditorSession(packs,temp.resolve("config"),meta,p->{});await();
    }
    private void await() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{do{Thread.sleep(10);session.poll();}while(session.busy());});
    }
    @Test void slotsCompareFolderAndZipVariantsAndKeepSearchThroughSwitchAndRefresh() throws Exception {
        prepare();session.filter(EditorSession.Tab.ALL,"ocean",session.packs().getFirst().id());
        session.browseArmorPart(ArmorPart.HELMET);assertEquals(TextureCategory.ARMOR,session.categoryFilter());assertEquals("",session.packFilter());assertEquals("",session.query());
        assertEquals(8,session.visible().size());assertEquals(2,session.visible().stream().map(TextureRef::pack).distinct().count());
        session.search("diamond");assertEquals(2,session.visible().size());
        for(ArmorPart part:new ArmorPart[]{ArmorPart.CHESTPLATE,ArmorPart.LEGGINGS,ArmorPart.BOOTS}) {
            session.browseArmorPart(part);assertEquals("diamond",session.query());assertEquals(2,session.visible().size());assertTrue(session.visible().stream().allMatch(part::matches));
        }
        session.refresh();await();assertEquals(ArmorPart.BOOTS,session.armorPartFilter());assertEquals(2,session.visible().size());
        session.browseArmorPart(ArmorPart.ALL);assertEquals("diamond",session.query());assertEquals(14,session.visible().size());
        session.search("");assertEquals(32,session.visible().size());
        session.browseCategory(TextureCategory.SWORDS);assertEquals(ArmorPart.ALL,session.armorPartFilter());assertEquals(2,session.visible().size());
        session.browseCategory(TextureCategory.ARMOR);assertEquals(32,session.visible().size());
        session.browseArmorPart(ArmorPart.HELMET);session.filter(EditorSession.Tab.FAVORITES,"","");assertEquals(ArmorPart.ALL,session.armorPartFilter());
    }
    @Test void armorButtonsSupportMouseKeyboardAndCategoryPopupRemainsModal() throws Exception {
        prepare();EditorCanvas canvas=new EditorCanvas(session,()->{});canvas.draw();
        canvas.click(250,160,0);canvas.draw();canvas.click(450,250,0);canvas.draw();assertEquals(TextureCategory.ARMOR,session.categoryFilter());
        canvas.click(320,195,0);canvas.draw();assertEquals(ArmorPart.HELMET,session.armorPartFilter());assertEquals(8,session.visible().size());
        canvas.click(250,80,0);for(char c:"diamond".toCharArray())canvas.type(c);canvas.draw();assertEquals(2,session.visible().size());
        canvas.click(320,195,0);canvas.draw();canvas.key(258,false,false,"",s->{});canvas.draw();canvas.key(257,false,false,"",s->{});canvas.draw();
        assertEquals(ArmorPart.CHESTPLATE,session.armorPartFilter());assertEquals("diamond",session.query());assertEquals(2,session.visible().size());
        canvas.click(600,195,0);canvas.draw();assertEquals(ArmorPart.LEGGINGS,session.armorPartFilter());
        canvas.click(750,195,0);canvas.draw();assertEquals(ArmorPart.BOOTS,session.armorPartFilter());
        canvas.click(250,160,0);canvas.draw();canvas.click(320,195,0);canvas.draw(); // Menu header covers the underlying armor buttons.
        assertEquals(ArmorPart.BOOTS,session.armorPartFilter());canvas.key(256,false,false,"",s->{});canvas.draw();
        canvas.click(200,195,0);canvas.draw();assertEquals(ArmorPart.ALL,session.armorPartFilter());assertEquals(14,session.visible().size());
        canvas.click(250,120,0);canvas.draw();assertTrue(canvas.showingPacks());assertEquals(ArmorPart.ALL,session.armorPartFilter());
    }
    @Test void rendersComparableArmorItemsAndKeepsAllSlotLabelsAccessibleAtMinimumWidth() throws Exception {
        prepare();session.browseArmorPart(ArmorPart.HELMET);session.search("diamond");EditorCanvas canvas=new EditorCanvas(session,()->{});
        for(int i=0;i<40;i++){canvas.draw();Thread.sleep(10);session.poll();}
        Path out=Path.of("build/previews");Files.createDirectories(out);canvas.mouseMove(200,240);
        ImageIO.write(canvas.draw(),"png",out.resolve("armor-helmets.png").toFile());
        canvas.resize(760,440);ImageIO.write(canvas.draw(),"png",out.resolve("armor-small.png").toFile());
        int[] centers={181,228,292,357,418};ArmorPart[] parts=ArmorPart.values();
        for(int i=0;i<centers.length;i++){canvas.click(centers[i],195,0);canvas.draw();assertEquals(parts[i],session.armorPartFilter());}
        canvas.click(292,195,0);ImageIO.write(canvas.draw(),"png",out.resolve("armor-chestplates-small.png").toFile());
        session.search("no-such-item");ImageIO.write(canvas.draw(),"png",out.resolve("armor-empty.png").toFile());assertTrue(session.visible().isEmpty());
    }
}
