package de.v0idpacks.core;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class CrystalSelectionTest {
    @TempDir Path temp;

    private Path source(String pack, Map<String, byte[]> textures) throws IOException {
        Path path = temp.resolve("packs").resolve(pack);
        Files.createDirectories(path.getParent());
        if (pack.endsWith(".zip")) {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
                for (var entry : textures.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
                }
            }
        } else {
            for (var entry : textures.entrySet()) {
                Path file = path.resolve(entry.getKey()); Files.createDirectories(file.getParent()); Files.write(file, entry.getValue());
            }
        }
        return path;
    }
    private Map<String, byte[]> textures(int color) throws IOException {
        return Map.of(CrystalTextures.ITEM, LibraryIntegrationTest.png(color),
                CrystalTextures.ENTITY, LibraryIntegrationTest.png(color + 1),
                CrystalTextures.ENTITY + ".mcmeta", "{\"animation\":{\"frametime\":3}}".getBytes(StandardCharsets.UTF_8));
    }
    private JsonObject metadata() { JsonObject value = new JsonObject(); value.addProperty("pack_format",46); return value; }

    @ParameterizedTest
    @CsvSource({"Pack,false", "Pack,true", "Pack.zip,false", "Pack.zip,true"})
    void eitherTextureSelectsAndExportsBothSnapshotsIncludingMetadata(String pack, boolean entityFirst) throws Exception {
        Map<String, byte[]> original = textures(0xff22aacc);
        source(pack, original);
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef(pack, entityFirst ? CrystalTextures.ENTITY : CrystalTextures.ITEM, ""), catalog);
        assertEquals(2, library.state().selected().size());
        assertTrue(library.state().selected().values().stream().allMatch(ref -> ref.pack().equals(pack)));
        // Subsequent source changes must not change either exported file or its metadata.
        source(pack, textures(0xffff4400));
        library = new LibraryStore(temp.resolve("config"));
        try (ZipFile zip = new ZipFile(PackExporter.export(library, catalog.root(), metadata()).toFile())) {
            for (var entry : original.entrySet())
                assertArrayEquals(entry.getValue(), zip.getInputStream(zip.getEntry(entry.getKey())).readAllBytes());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void choosingAnotherPackReplacesBothAndRemovingEitherRemovesBoth(boolean entityFirst) throws Exception {
        source("A", textures(0xff22aacc)); source("B.zip", textures(0xffff4400));
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("A", CrystalTextures.ITEM, ""), catalog);
        String chosen = entityFirst ? CrystalTextures.ENTITY : CrystalTextures.ITEM;
        library.select(new TextureRef("B.zip", chosen, ""), catalog);
        assertEquals(2, library.state().selected().size());
        assertTrue(library.state().selected().values().stream().allMatch(ref -> ref.pack().equals("B.zip")));
        library.remove(chosen);
        assertTrue(new LibraryStore(temp.resolve("config")).state().selected().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void missingOrCorruptPartnerCannotPartiallyReplacePreviousPair(boolean entityFirst, boolean corrupt) throws Exception {
        source("A", textures(0xff22aacc));
        String chosen = entityFirst ? CrystalTextures.ENTITY : CrystalTextures.ITEM;
        Map<String, byte[]> incomplete = new HashMap<>(textures(0xffff4400));
        if (corrupt) incomplete.put(CrystalTextures.companion(chosen), new byte[]{1,2,3});
        else incomplete.remove(CrystalTextures.companion(chosen));
        source("Broken", incomplete);
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("A", chosen, ""), catalog);
        var previous = library.state();
        assertThrows(IOException.class, () -> library.select(new TextureRef("Broken", chosen, ""), catalog));
        assertEquals(previous, library.state());
        assertEquals(previous, new LibraryStore(temp.resolve("config")).state());
    }

    @ParameterizedTest @ValueSource(strings = {"item", "entity", "mixed"})
    void legacyIncompleteOrMixedPairsCannotExportAndCanBeRepaired(String kind) throws Exception {
        source("A", textures(0xff22aacc)); source("B", textures(0xffff4400));
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        Map<String, TextureRef> selected = new HashMap<>();
        if (!kind.equals("entity")) selected.put(CrystalTextures.ITEM, library.snapshot(new TextureRef("A", CrystalTextures.ITEM, ""), catalog));
        if (!kind.equals("item")) selected.put(CrystalTextures.ENTITY, library.snapshot(new TextureRef("B", CrystalTextures.ENTITY, ""), catalog));
        Files.writeString(library.root().resolve("library.json"), new Gson().toJson(new LibraryStore.State(1,"Legacy",Map.of(),selected)));
        LibraryStore legacy = new LibraryStore(library.root());
        IOException failure = assertThrows(IOException.class, () -> PackExporter.export(legacy,catalog.root(),metadata()));
        assertTrue(failure.getMessage().contains("erneut übernehmen"));
        try (var files = Files.list(catalog.root())) { assertFalse(files.anyMatch(path -> path.toString().endsWith(".zip"))); }
        legacy.select(new TextureRef("A", CrystalTextures.ITEM, ""), catalog);
        assertTrue(Files.isRegularFile(PackExporter.export(legacy,catalog.root(),metadata())));
    }
}
