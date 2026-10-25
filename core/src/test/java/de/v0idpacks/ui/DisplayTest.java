package de.v0idpacks.ui;

import com.google.gson.JsonParser;
import de.v0idpacks.core.MinecraftFormatting;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class DisplayTest {
    @Test void minecraftStylesColorsAndResetsAreParsedWithoutChangingVisibleNames() {
        var runs=MinecraftFormatting.parse("§b§lOcean §r16x &6Ember &#ab34ef RGB §x§1§2§3§4§5§6Hex");
        assertEquals("Ocean 16x Ember  RGB Hex",MinecraftFormatting.plain("§b§lOcean §r16x &6Ember &#ab34ef RGB §x§1§2§3§4§5§6Hex"));
        assertEquals(0x55ffff,runs.get(0).color());assertTrue(runs.get(0).bold());assertFalse(runs.get(1).bold());assertNull(runs.get(1).color());
        assertEquals(0xffaa00,runs.get(2).color());assertEquals(0xab34ef,runs.get(3).color());assertEquals(0x123456,runs.get(4).color());
        assertEquals("Fish & Chips §q",MinecraftFormatting.plain("Fish & Chips §q"));
    }
    @Test void jsonComponentChildrenCanOverrideStylesAndDoNotLeakIntoSiblings() {
        String text=MinecraftFormatting.component(JsonParser.parseString("""
                [{"text":"A","color":"gold","bold":true,"extra":[{"text":"B","bold":false}]},{"text":"C"}]
                """));
        var runs=MinecraftFormatting.parse(text);
        assertEquals("ABC",MinecraftFormatting.plain(text));assertTrue(runs.get(0).bold());assertFalse(runs.get(1).bold());assertEquals(0xffaa00,runs.get(1).color());assertNull(runs.get(2).color());
    }
    @Test void formattedTextClippingRetainsColorsAndFitsTheAvailableWidth() {
        var g=new BufferedImage(200,50,BufferedImage.TYPE_INT_ARGB).createGraphics();Font font=new Font(Font.SANS_SERIF,Font.PLAIN,13);
        String full="§b§lOcean texture collection",clipped=FormattedText.clip(g,full,90,font);
        assertTrue(FormattedText.width(g,clipped,font)<=90);assertTrue(MinecraftFormatting.plain(clipped).endsWith("…"));assertEquals(0x55ffff,MinecraftFormatting.parse(clipped).getFirst().color());g.dispose();
    }
    @Test void panelUsesPhysicalPixelsAtEveryMinecraftGuiScaleAndMapsClicks() {
        for(int scale=1;scale<=6;scale++) {
            int guiW=(int)Math.ceil(1920./scale),guiH=(int)Math.ceil(1080./scale);
            PanelLayout panel=PanelLayout.fit(guiW,guiH,1920,1080);
            assertTrue(panel.pixelWidth()<=1120);assertTrue(panel.pixelHeight()<=660);assertTrue(panel.x()>0);assertTrue(panel.y()>0);
            assertEquals(panel.pixelWidth(),panel.width()*1920./guiW,.51);
            assertEquals(panel.pixelHeight(),panel.height()*1080./guiH,.51);
            assertEquals(250,panel.mouseX(panel.x()+250.*panel.width()/panel.pixelWidth()),1);
            assertEquals(180,panel.mouseY(panel.y()+180.*panel.height()/panel.pixelHeight()),1);
            assertEquals(20,panel.dragX(20.*panel.width()/panel.pixelWidth()),.0001);
        }
    }
}
