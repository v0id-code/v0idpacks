package de.v0idpacks.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PackPresentationTest {
    @TempDir Path temp;
    private static final String TEXTURE = "assets/minecraft/textures/item/diamond.png";
    private LibraryStore library() throws Exception {
        Path source = temp.resolve("packs/Source");
        Files.createDirectories(source.resolve(TEXTURE).getParent());
        Files.writeString(source.resolve("pack.mcmeta"), "{}");
        Files.write(source.resolve(TEXTURE), LibraryIntegrationTest.png(0xffabcdef));
        LibraryStore store = new LibraryStore(temp.resolve("config"));
        store.select(new TextureRef("Source", TEXTURE, ""), new PackCatalog(temp.resolve("packs")));
        return store;
    }
    private Path export(LibraryStore store) throws Exception {
        JsonObject format = new JsonObject(); format.addProperty("pack_format", 46);
        return PackExporter.export(store, temp.resolve("packs"), format);
    }
    @Test void nameAndLogoPersistWithoutOriginalFileAndExportTogether() throws Exception {
        LibraryStore store = library();
        byte[] png = LibraryIntegrationTest.png(0xff22aaff);
        Path logo = temp.resolve("logo.png"); Files.write(logo, png);
        store.importLogo(logo); store.rename("Linus' Über-Pack: PvP"); Files.delete(logo);
        store.toggleFavorite(store.state().selected().get(TEXTURE), new PackCatalog(temp.resolve("packs")));
        store = new LibraryStore(store.root());
        assertArrayEquals(png, store.readLogo(store.state()));
        Path output = export(store);
        assertTrue(output.getFileName().toString().startsWith("Linus' Über-Pack_ PvP [v0idpacks-"));
        assertTrue(PackCatalog.isGeneratedPack(output.getFileName().toString()));
        assertEquals(store.state().name(), PackPresentation.readName(output.getParent(), "file/" + output.getFileName()));
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertArrayEquals(png, zip.getInputStream(zip.getEntry("pack.png")).readAllBytes());
        }
        assertEquals(1, new PackCatalog(output.getParent()).scan().packs().size());
        assertEquals(output, export(store));
    }
    @Test void changingAndRemovingLogoKeepsPreviousExportsIntact() throws Exception {
        LibraryStore store = library(); Path first = export(store); byte[] before = Files.readAllBytes(first);
        Path logo = temp.resolve("logo.png"); Files.write(logo, LibraryIntegrationTest.png(0xffabcdef));
        store.importLogo(logo); Path second = export(store);
        Files.write(logo, LibraryIntegrationTest.png(0xff123456)); store.importLogo(logo); Path third = export(store);
        assertNotEquals(first, second); assertNotEquals(second, third);
        assertArrayEquals(before, Files.readAllBytes(first));
        store.removeLogo(); assertEquals(first, export(store));
        assertTrue(new LibraryStore(store.root()).state().logo().isEmpty());
    }
    @Test void invalidLogoLeavesSavedLogoAndExportsUntouched() throws Exception {
        LibraryStore store = library(); Path file = temp.resolve("logo.png");
        Files.write(file, LibraryIntegrationTest.png(0xffabcdef)); store.importLogo(file);
        String hash = store.state().logo(); Path output = export(store); byte[] before = Files.readAllBytes(output);
        Files.write(file, new byte[]{1,2,3}); assertThrows(java.io.IOException.class, () -> store.importLogo(file));
        ImageIO.write(new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        assertThrows(java.io.IOException.class, () -> store.importLogo(file));
        assertEquals(hash, store.state().logo());
        Files.write(store.root().resolve("logos").resolve(hash + ".png"), new byte[]{1,2,3});
        assertThrows(java.io.IOException.class, () -> export(store));
        assertArrayEquals(before, Files.readAllBytes(output));
    }
    @Test void oldProjectLoadsAndUnrelatedPacksAreNotRenamed() throws Exception {
        Path root = temp.resolve("old"); Files.createDirectories(root);
        Files.writeString(root.resolve("library.json"), "{\"schema\":1,\"name\":\"Altes Pack\",\"favorites\":{},\"selected\":{}}");
        LibraryStore store = new LibraryStore(root);
        assertEquals("", store.state().logo()); assertEquals("Altes Pack", store.state().name());
        assertNull(PackPresentation.readName(temp, "vanilla"));
        assertNull(PackPresentation.readName(temp, "file/Unrelated.zip"));
        assertFalse(PackCatalog.isGeneratedPack("../Name [v0idpacks-" + "a".repeat(32) + "].zip"));
        assertTrue(PackCatalog.isGeneratedPack(PackCatalog.OUTPUT_PREFIX + "a".repeat(32) + ".zip"));
    }
}
