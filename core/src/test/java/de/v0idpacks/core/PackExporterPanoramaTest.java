package de.v0idpacks.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PackExporterPanoramaTest {
    private static final String BACKGROUND = "assets/minecraft/textures/gui/title/background/";
    @TempDir Path temp;
    private Path packs;
    private PackCatalog catalog;
    private LibraryStore library;

    @BeforeEach void setup() throws Exception {
        packs = temp.resolve("resourcepacks");
        catalog = new PackCatalog(packs);
        library = new LibraryStore(temp.resolve("config"));
    }

    private void select(String pack, String path, int width, int height) throws IOException {
        Path source = packs.resolve(pack);
        Files.createDirectories(source.resolve(path).getParent());
        Files.writeString(source.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Test\"}}");
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", source.resolve(path).toFile());
        library.select(new TextureRef(pack, path, ""), catalog);
    }

    private void completePanorama(int size) throws IOException {
        for (int i = 0; i < 6; i++) select("Source", BACKGROUND + "panorama_" + i + ".png", size, size);
    }

    private Path export() throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("pack_format", 46);
        return PackExporter.export(library, packs, metadata);
    }

    private void assertNoExportOrTemp() throws IOException {
        try (var files = Files.list(packs)) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".tmp")));
        }
    }

    @Test void rejectsSingleFaceEvenWhenItsSourceContainsAllSix() throws Exception {
        completePanorama(16);
        for (int i = 1; i < 6; i++) library.remove(BACKGROUND + "panorama_" + i + ".png");
        IOException error = assertThrows(IOException.class, this::export);
        assertTrue(error.getMessage().contains("panorama_1.png fehlt"));
        assertEquals(1, library.state().selected().size());
        assertNoExportOrTemp();
    }

    @Test void rejectsMissingLastFace() throws Exception {
        completePanorama(16);
        library.remove(BACKGROUND + "panorama_5.png");
        assertTrue(assertThrows(IOException.class, this::export).getMessage().contains("panorama_5.png fehlt"));
        assertNoExportOrTemp();
    }

    @Test void rejectsReported1024Versus256MismatchAndPreservesPreviousExport() throws Exception {
        completePanorama(1024);
        Path previous = export();
        byte[] previousBytes = Files.readAllBytes(previous);
        select("Other", BACKGROUND + "panorama_4.png", 256, 256);
        IOException error = assertThrows(IOException.class, this::export);
        assertTrue(error.getMessage().contains("panorama_4.png"));
        assertTrue(error.getMessage().contains("256x256 statt 1024x1024"));
        assertArrayEquals(previousBytes, Files.readAllBytes(previous));
        try (var files = Files.list(packs)) {
            assertEquals(java.util.List.of(previous), files.filter(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".tmp")).toList());
        }
    }

    @Test void rejectsRectangularSidesEvenWhenAllDimensionsMatch() throws Exception {
        for (int i = 0; i < 6; i++) select("Source", BACKGROUND + "panorama_" + i + ".png", 16, 32);
        IOException error = assertThrows(IOException.class, this::export);
        assertTrue(error.getMessage().contains("16x32"));
        assertTrue(error.getMessage().contains("quadratisch"));
        assertNoExportOrTemp();
    }

    @Test void exportsEqualSquareSidesFromDifferentSourcesUnchanged() throws Exception {
        for (int i = 0; i < 6; i++) select("Source" + i, BACKGROUND + "panorama_" + i + ".png", 16, 16);
        // Use saved snapshots, even if the original source has changed.
        Files.delete(packs.resolve("Source0").resolve(BACKGROUND + "panorama_0.png"));
        try (ZipFile zip = new ZipFile(export().toFile())) {
            assertEquals(7, zip.size());
            for (var face : library.state().selected().values()) {
                assertArrayEquals(library.read(face).png(), zip.getInputStream(zip.getEntry(face.path())).readAllBytes());
            }
        }
    }

    @Test void ignoresOverlayAndUnrelatedTexturesNamedPanorama() throws Exception {
        select("Source", BACKGROUND + "panorama_overlay.png", 16, 32);
        select("Source", "assets/minecraft/textures/item/panorama_0.png", 16, 16);
        assertTrue(Files.exists(export()));
    }

    @Test void doesNotCombineFacesFromDifferentNamespaces() throws Exception {
        for (int i = 0; i < 6; i++) {
            String background = i == 5 ? BACKGROUND.replace("/minecraft/", "/example/") : BACKGROUND;
            select("Source", background + "panorama_" + i + ".png", 16, 16);
        }
        assertTrue(assertThrows(IOException.class, this::export).getMessage().contains("unvollständig"));
        assertNoExportOrTemp();
    }

    @Test void separateNamespacesMayUseDifferentResolutions() throws Exception {
        completePanorama(16);
        for (int i = 0; i < 6; i++) select("Source", BACKGROUND.replace("/minecraft/", "/example/") + "panorama_" + i + ".png", 32, 32);
        try (ZipFile zip = new ZipFile(export().toFile())) { assertEquals(13, zip.size()); }
    }
}
