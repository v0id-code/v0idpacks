package de.v0idpacks.core;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelExportTest {
    @TempDir Path temp;
    private static final String APPLE = "assets/minecraft/textures/item/golden_apple.png";
    private static final String APPLE_MODEL = "assets/minecraft/models/item/golden_apple.json";
    private static final String PARENT = "assets/custom/models/small.json";
    private static byte[] json(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private Path source(String name, Map<String, byte[]> assets) throws IOException {
        Path path = temp.resolve("packs").resolve(name);
        Files.createDirectories(path.getParent());
        if (name.endsWith(".zip")) {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
                for (var entry : assets.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
                }
            }
        } else {
            for (var entry : assets.entrySet()) {
                Path file = path.resolve(entry.getKey()); Files.createDirectories(file.getParent()); Files.write(file, entry.getValue());
            }
        }
        return path;
    }
    private Path export(LibraryStore library) throws IOException {
        return PackExporter.export(library, temp.resolve("exports"), JsonParser.parseString("{\"pack_format\":46}").getAsJsonObject());
    }
    private void assertAssets(Path file, Map<String, byte[]> expected) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            for (var entry : expected.entrySet()) {
                ZipEntry actual = zip.getEntry(entry.getKey());
                if (actual == null && entry.getKey().contains("/models/")) {
                    String[] parts = entry.getKey().split("/", 4);
                    actual = zip.stream().filter(e -> e.getName().startsWith("assets/v0idpacks/models/source/")
                            && e.getName().endsWith("/" + parts[1] + "/" + parts[3])).findFirst().orElse(null);
                }
                assertNotNull(actual, entry.getKey());
                byte[] bytes = zip.getInputStream(actual).readAllBytes();
                if (entry.getKey().endsWith(".json")) assertEquals(normalized(entry.getValue()), normalized(bytes), entry.getKey());
                else assertArrayEquals(entry.getValue(), bytes, entry.getKey());
            }
        }
    }
    private JsonElement normalized(byte[] bytes) {
        JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        normalize(json);
        return json;
    }
    private void normalize(JsonElement json) {
        if (json.isJsonArray()) for (JsonElement child : json.getAsJsonArray()) normalize(child);
        else if (json.isJsonObject()) for (var entry : json.getAsJsonObject().entrySet()) {
            JsonElement child = entry.getValue();
            if (Set.of("parent", "model", "base").contains(entry.getKey()) && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                String id = child.getAsString().replaceFirst("^v0idpacks:source/[a-f0-9]{64}/([^/]+)/", "$1:");
                entry.setValue(new JsonPrimitive(id.contains(":") ? id : "minecraft:" + id));
            } else normalize(child);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Pack", "Pack.zip"})
    void smallAppleKeepsParentTransformsAndAnimatedDependenciesAfterSourceRemoval(String pack) throws Exception {
        String extra = "assets/custom/textures/detail.png";
        Map<String, byte[]> assets = Map.of(APPLE, LibraryIntegrationTest.png(0xff112233),
                APPLE_MODEL, json("{\"parent\":\"custom:small\",\"textures\":{\"layer0\":\"item/golden_apple\",\"layer1\":\"custom:detail\"}}"),
                PARENT, json("{\"parent\":\"item/generated\",\"display\":{\"firstperson_righthand\":{\"scale\":[0.3,0.3,0.3],\"translation\":[1,2,3]}}}"),
                extra, LibraryIntegrationTest.png(0xff445566), extra + ".mcmeta", json("{\"animation\":{\"frametime\":3}}"));
        Path source = source(pack, assets);
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.toggleFavorite(new TextureRef(pack, APPLE, ""), catalog);
        Files.move(source, source.resolveSibling("removed"));
        library = new LibraryStore(library.root());
        library.select(library.state().favorites().values().iterator().next(), catalog);
        assertAssets(export(library), assets);
    }

    @ParameterizedTest @ValueSource(strings = {"Pack", "Pack.zip"})
    void shieldAtlasIncludesBothSpecialModelBasesAndAllConditionalBranches(String pack) throws Exception {
        String shield = "assets/minecraft/textures/entity/shield_base_nopattern.png";
        Map<String, byte[]> assets = Map.of(shield, LibraryIntegrationTest.png(0xff112233),
                "assets/minecraft/items/shield.json", json("""
                {"model":{"type":"minecraft:condition","property":"minecraft:using_item",
                  "on_true":{"type":"minecraft:special","base":"custom:blocking","model":{"type":"minecraft:shield"}},
                  "on_false":{"type":"minecraft:special","base":"custom:normal","model":{"type":"minecraft:shield"}}}}
                """),
                "assets/custom/models/normal.json", json("{\"display\":{\"firstperson_righthand\":{\"scale\":[0.5,0.5,0.5]}}}"),
                "assets/custom/models/blocking.json", json("{\"parent\":\"custom:normal\"}"));
        source(pack, assets);
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef(pack, shield, ""), new PackCatalog(temp.resolve("packs")));
        assertAssets(export(library), assets);
    }

    @Test void legacyShieldBlockingOverrideIsAlsoExported() throws Exception {
        String shield = "assets/minecraft/textures/entity/shield/base.png";
        Map<String, byte[]> assets = Map.of(shield, LibraryIntegrationTest.png(0xff112233),
                "assets/minecraft/models/item/shield.json", json("{\"parent\":\"builtin/entity\",\"overrides\":[{\"predicate\":{\"blocking\":1},\"model\":\"item/shield_blocking\"}]}"),
                "assets/minecraft/models/item/shield_blocking.json", json("{\"display\":{\"firstperson_righthand\":{\"scale\":[0.4,0.4,0.4]}}}"));
        source("Pack", assets);
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("Pack", shield, ""), new PackCatalog(temp.resolve("packs")));
        assertAssets(export(library), assets);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void crystalKeepsItsActualItemGeometryInsteadOfExportingOnlyTheFlatAtlas(boolean entityFirst) throws Exception {
        Map<String, byte[]> assets = Map.of(CrystalTextures.ITEM, LibraryIntegrationTest.png(0xff112233),
                CrystalTextures.ENTITY, LibraryIntegrationTest.png(0xff445566),
                "assets/minecraft/items/end_crystal.json", json("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"custom:crystal\"}}"),
                "assets/custom/models/crystal.json", json("""
                {"textures":{"all":"minecraft:item/end_crystal"},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{"north":{"texture":"#all","uv":[4,12,8,16]}}}],
                 "display":{"gui":{"rotation":[45,45,0],"scale":[0.45,0.45,0.45]}}}
                """));
        source("Pack.zip", assets);
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("Pack.zip", entityFirst ? CrystalTextures.ENTITY : CrystalTextures.ITEM, ""), new PackCatalog(temp.resolve("packs")));
        Files.delete(temp.resolve("packs/Pack.zip"));
        assertAssets(export(new LibraryStore(library.root())), assets);
    }

    @Test void changedModelsWithIdenticalPixelsProduceNewExportAndReplacementRemovesOldDependencies() throws Exception {
        byte[] png = LibraryIntegrationTest.png(0xff112233);
        source("A", Map.of(APPLE, png, APPLE_MODEL, json("{\"parent\":\"custom:small\"}"), PARENT, json("{\"display\":{\"gui\":{\"scale\":[0.3,0.3,0.3]}}}")));
        source("B", Map.of(APPLE, png));
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("A", APPLE, ""), catalog);
        Path first = export(library); byte[] previous = Files.readAllBytes(first);
        source("A", Map.of(PARENT, json("{\"display\":{\"gui\":{\"scale\":[0.6,0.6,0.6]}}}")));
        library.select(new TextureRef("A", APPLE, ""), catalog);
        assertNotEquals(first, export(library)); assertArrayEquals(previous, Files.readAllBytes(first));
        library.select(new TextureRef("B", APPLE, ""), catalog);
        try (ZipFile zip = new ZipFile(export(library).toFile())) { assertNull(zip.getEntry(APPLE_MODEL)); assertNull(zip.getEntry(PARENT)); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void crystalFavoriteKeepsWholePairAndModelsAfterDeletingSource(boolean entityFirst) throws Exception {
        Map<String, byte[]> assets = Map.of(CrystalTextures.ITEM, LibraryIntegrationTest.png(0xff112233),
                CrystalTextures.ENTITY, LibraryIntegrationTest.png(0xff445566),
                "assets/minecraft/models/item/end_crystal.json", json("{\"textures\":{\"all\":\"item/end_crystal\"},\"elements\":[]}"));
        source("Pack.zip", assets);
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.toggleFavorite(new TextureRef("Pack.zip", entityFirst ? CrystalTextures.ENTITY : CrystalTextures.ITEM, ""), catalog);
        Files.delete(temp.resolve("packs/Pack.zip"));
        library = new LibraryStore(library.root());
        library.select(library.state().favorites().values().iterator().next(), catalog);
        assertAssets(export(library), assets);
    }

    @Test void unsafeOrMalformedModelsCannotReplaceSelectionAndCorruptSnapshotsCannotExport() throws Exception {
        source("Pack", Map.of(APPLE, LibraryIntegrationTest.png(0xff112233), APPLE_MODEL, json("{\"parent\":\"custom:small\"}"), PARENT, json("{}")));
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("Pack", APPLE, ""), catalog);
        var before = library.state();
        for (String bad : List.of("{broken", "{\"parent\":\"../outside\"}")) {
            source("Pack", Map.of(APPLE_MODEL, json(bad)));
            assertThrows(IOException.class, () -> library.select(new TextureRef("Pack", APPLE, ""), catalog));
            assertEquals(before, library.state());
        }
        String hash = before.selected().get(APPLE).assets().get(PARENT);
        Files.writeString(library.root().resolve("assets").resolve(hash), "changed");
        assertThrows(IOException.class, () -> export(library));
    }

    @Test void conflictingParentsAreIsolatedSoItemsKeepTheirOwnSizes() throws Exception {
        String carrot = "assets/minecraft/textures/item/carrot.png";
        source("A", Map.of(APPLE, LibraryIntegrationTest.png(0xff112233), APPLE_MODEL, json("{\"parent\":\"custom:small\"}"), PARENT, json("{}")));
        source("B", Map.of(carrot, LibraryIntegrationTest.png(0xff445566), "assets/minecraft/models/item/carrot.json", json("{\"parent\":\"custom:small\"}"), PARENT, json("{\"display\":{\"gui\":{\"scale\":[0.2,0.2,0.2]}}}")));
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.select(new TextureRef("A", APPLE, ""), catalog);
        library.select(new TextureRef("B", carrot, ""), catalog);
        try (ZipFile zip = new ZipFile(export(library).toFile())) {
            assertNull(zip.getEntry(PARENT), "Shared parent must not override unrelated items");
            JsonObject apple = readJson(zip, APPLE_MODEL);
            JsonObject carrotModel = readJson(zip, "assets/minecraft/models/item/carrot.json");
            String appleParent = apple.get("parent").getAsString(), carrotParent = carrotModel.get("parent").getAsString();
            assertNotEquals(appleParent, carrotParent);
            assertEquals(new JsonObject(), readJson(zip, modelPath(appleParent)));
            assertEquals(0.2, readJson(zip, modelPath(carrotParent)).getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("scale").get(0).getAsDouble());
        }
    }
    private String modelPath(String id) { return "assets/" + id.replace(":", "/models/") + ".json"; }
    private JsonObject readJson(ZipFile zip, String path) throws IOException {
        return JsonParser.parseString(new String(zip.getInputStream(zip.getEntry(path)).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
