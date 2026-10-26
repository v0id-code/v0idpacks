package de.v0idpacks.ui;

import com.google.gson.*;
import de.v0idpacks.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class CrystalWorkshopTest {
    @TempDir Path temp;
    private EditorSession session;
    private static final int ITEM_A = 0xff22aacc, ENTITY_A = 0xffcc44aa, OTHER = 0xffee8800;

    @AfterEach void close() { if (session != null) session.close(); }
    private JsonObject metadata() { JsonObject value = new JsonObject(); value.addProperty("pack_format",46); return value; }
    private void texture(String pack, String path, int color) throws Exception {
        Path file = temp.resolve("packs").resolve(pack).resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(temp.resolve("packs").resolve(pack).resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46}}");
        BufferedImage image = new BufferedImage(path.equals(CrystalTextures.ENTITY) ? 64 : 16, path.equals(CrystalTextures.ENTITY) ? 32 : 16, BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) image.setRGB(x,y,color);
        ImageIO.write(image,"png",file.toFile());
    }
    private void pair(String pack, int item, int entity) throws Exception {
        texture(pack,CrystalTextures.ITEM,item); texture(pack,CrystalTextures.ENTITY,entity);
    }
    private void open() {
        session = new EditorSession(temp.resolve("packs"),temp.resolve("config"),metadata(),path -> {},path -> {
            if (path.contains("end_crystal")) fail("Crystal preview must not use an active-pack fallback: " + path);
            return null;
        });
        idle();
    }
    private void waitFor(BooleanSupplier done) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            do { Thread.sleep(10); session.poll(); } while (!done.getAsBoolean());
        });
    }
    private void idle() { waitFor(() -> !session.busy()); }
    private void preview() { waitFor(() -> session.preview() != null || !session.previewError().isEmpty()); assertEquals("",session.previewError()); }
    private TextureRef ref(String pack, String path) {
        return session.visible().stream().filter(ref -> ref.pack().equals(pack) && ref.path().equals(path)).findFirst().orElseThrow();
    }
    private void assertPreview(int item, int entity) {
        preview(); assertEquals(item,session.preview().frame().getRGB(0,0));
        assertNotNull(session.previewModel(),session.modelNote());
        for (var quad : session.previewModel().quads()) assertEquals(entity,quad.texture().getRGB(0,0));
    }

    @Test void selectedPreviewAndExportUseTheSameSavedPairAfterSourceChangesAndRemoval() throws Exception {
        pair("A",ITEM_A,ENTITY_A); open();
        TextureRef item = ref("A",CrystalTextures.ITEM), entity = ref("A",CrystalTextures.ENTITY);
        session.focus(item); session.select(); idle();
        assertEquals(2,session.selectedCount()); assertTrue(session.isSelected(item)); assertTrue(session.isSelected(entity));
        assertPreview(ITEM_A,ENTITY_A);
        pair("A",OTHER,OTHER);
        session.filter(EditorSession.Tab.SELECTED,"",""); assertPreview(ITEM_A,ENTITY_A);
        session.refresh(); idle(); assertPreview(ITEM_A,ENTITY_A);
        // Even the already-selected source tile must display the saved export, not changed source pixels.
        session.filter(EditorSession.Tab.ALL,"",""); assertPreview(ITEM_A,ENTITY_A);
        Files.delete(temp.resolve("packs/A").resolve(CrystalTextures.ITEM));
        Files.delete(temp.resolve("packs/A").resolve(CrystalTextures.ENTITY));
        session.close(); open();
        session.filter(EditorSession.Tab.SELECTED,"",""); session.focus(ref("A",CrystalTextures.ITEM));
        assertPreview(ITEM_A,ENTITY_A);
        session.export(false); idle(); assertFalse(session.error(),session.status());
        Path exported;
        try (var files = Files.list(temp.resolve("packs"))) { exported = files.filter(path -> path.toString().endsWith(".zip")).findFirst().orElseThrow(); }
        try (ZipFile zip = new ZipFile(exported.toFile())) {
            BufferedImage icon = ImageIO.read(zip.getInputStream(zip.getEntry(CrystalTextures.ITEM)));
            BufferedImage atlas = ImageIO.read(zip.getInputStream(zip.getEntry(CrystalTextures.ENTITY)));
            assertEquals(icon.getRGB(0,0),session.preview().frame().getRGB(0,0));
            assertEquals(atlas.getRGB(0,0),session.previewModel().quads().getFirst().texture().getRGB(0,0));
        }
        session.focus(ref("A",CrystalTextures.ENTITY)); preview();
        assertEquals(ENTITY_A,session.preview().frame().getRGB(0,0));
        session.select(); idle(); assertEquals(0,session.selectedCount()); assertNull(session.focused());
    }

    @Test void replacingFromTheEntityTileUpdatesBothFilesAndTheItemPreview() throws Exception {
        pair("A",ITEM_A,ENTITY_A); pair("B",OTHER,OTHER); open();
        session.focus(ref("A",CrystalTextures.ITEM)); session.select(); idle();
        session.focus(ref("B",CrystalTextures.ENTITY)); session.select(); idle();
        assertEquals(2,session.selectedCount());
        assertFalse(session.isSelected(ref("A",CrystalTextures.ITEM)));
        session.filter(EditorSession.Tab.SELECTED,"","");
        session.focus(ref("B",CrystalTextures.ITEM)); assertPreview(OTHER,OTHER);
    }

    @Test void missingSourcePartnerShowsNoUnrelated3dPreviewAndCannotBeSelected() throws Exception {
        texture("Partial",CrystalTextures.ITEM,ITEM_A); open();
        session.focus(ref("Partial",CrystalTextures.ITEM)); preview();
        assertNull(session.previewModel()); assertTrue(session.modelNote().contains("im Quell-Pack"));
        session.select(); idle();
        assertTrue(session.error()); assertTrue(session.status().contains("fehlt im Quell-Pack")); assertEquals(0,session.selectedCount());
    }

    @Test void legacyIncompleteSelectionIsShownHonestlyAndReselectingRepairsIt() throws Exception {
        pair("A",ITEM_A,ENTITY_A);
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        TextureRef saved = library.snapshot(new TextureRef("A",CrystalTextures.ITEM,""),catalog);
        Files.writeString(library.root().resolve("library.json"),new Gson().toJson(new LibraryStore.State(1,"Legacy",Map.of(),Map.of(saved.path(),saved))));
        open(); session.filter(EditorSession.Tab.SELECTED,"","");
        TextureRef item = ref("A",CrystalTextures.ITEM); session.focus(item); preview();
        assertTrue(session.isSelected(item));
        assertNull(session.previewModel()); assertTrue(session.modelNote().contains("im Projekt"));
        session.export(false); idle(); assertTrue(session.error());
        session.filter(EditorSession.Tab.ALL,"",""); session.focus(ref("A",CrystalTextures.ITEM));
        assertFalse(session.isSelected(item)); assertTrue(session.replacesOther(item));
        session.select(); idle(); assertEquals(2,session.selectedCount()); assertPreview(ITEM_A,ENTITY_A);
        session.export(false); idle(); assertFalse(session.error(),session.status());
    }

    @Test void legacyOrphanCanStillBeRemovedFromTheProjectWhenTheSourceIsGone() throws Exception {
        pair("A",ITEM_A,ENTITY_A);
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        TextureRef saved = library.snapshot(new TextureRef("A",CrystalTextures.ITEM,""),catalog);
        Files.writeString(library.root().resolve("library.json"),new Gson().toJson(new LibraryStore.State(1,"Legacy",Map.of(),Map.of(saved.path(),saved))));
        Files.delete(temp.resolve("packs/A").resolve(CrystalTextures.ITEM));
        Files.delete(temp.resolve("packs/A").resolve(CrystalTextures.ENTITY));
        open(); session.filter(EditorSession.Tab.SELECTED,"",""); session.focus(ref("A",CrystalTextures.ITEM));
        assertTrue(session.isSelected(saved));
        session.select(); idle(); assertFalse(session.error(),session.status());
        assertEquals(0,session.selectedCount()); assertNull(session.focused());
    }
}
