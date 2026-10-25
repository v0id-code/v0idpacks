package de.v0idpacks.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PackInfoTest {
    @TempDir Path temp;
    @Test void folderAndZipIconsDescriptionsAndFormattedNamesAreRead() throws Exception {
        BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xff00ff44);
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(image,"png",png);
        byte[] meta="{\"pack\":{\"description\":{\"text\":\"Ocean\",\"color\":\"aqua\"},\"pack_format\":46}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path folder=temp.resolve("§bOcean");Files.createDirectories(folder);Files.write(folder.resolve("pack.mcmeta"),meta);Files.write(folder.resolve("pack.png"),png.toByteArray());
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temp.resolve("&6Gold.zip")))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write(meta);zip.closeEntry();zip.putNextEntry(new ZipEntry("pack.png"));zip.write(png.toByteArray());zip.closeEntry();
        }
        PackCatalog catalog=new PackCatalog(temp);var scan=catalog.scan();assertEquals(2,scan.packs().size());assertTrue(scan.warnings().isEmpty());
        for(PackInfo pack:scan.packs()){assertEquals(64,pack.icon().getWidth());assertEquals(0xff00ff44,pack.icon().getRGB(0,0));assertEquals("Ocean",MinecraftFormatting.plain(pack.description()));assertTrue(pack.matches("ocean"));}
        assertEquals("&6Gold",scan.packs().getFirst().displayName());assertNull(catalog.readAsset("&6Gold.zip","assets/minecraft/models/item/missing.json"));
        assertThrows(IOException.class,()->catalog.readAsset("../outside","pack.png"));assertThrows(IOException.class,()->catalog.readAsset("§bOcean","assets/minecraft/../../private.json"));
    }
    @Test void missingOrInvalidIconKeepsPackBrowsableWithFallback() throws Exception {
        for(String name:new String[]{"missing","invalid"}){Path p=temp.resolve(name);Files.createDirectories(p);Files.writeString(p.resolve("pack.mcmeta"),"{\"pack\":{\"description\":\"Hello\"}}");}
        Files.writeString(temp.resolve("invalid/pack.png"),"invalid PNG");var scan=new PackCatalog(temp).scan();
        assertEquals(2,scan.packs().size());assertTrue(scan.packs().stream().allMatch(p->p.icon()==null));assertEquals(1,scan.warnings().size());
    }
}
