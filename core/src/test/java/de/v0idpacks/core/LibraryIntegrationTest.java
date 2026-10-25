package de.v0idpacks.core;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class LibraryIntegrationTest {
    @TempDir Path temp;
    private static final String SWORD = "assets/minecraft/textures/item/diamond_sword.png";
    private Path packs;
    private PackCatalog catalog;
    @BeforeEach void setup() throws Exception { packs = temp.resolve("resourcepacks"); catalog = new PackCatalog(packs); }
    static byte[] png(int argb) throws IOException {
        BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=2;y<14;y++) for(int x=6;x<10;x++) image.setRGB(x,y,argb);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    private Path folder(String name, String path, byte[] bytes) throws IOException {
        Path root = packs.resolve(name); Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Test\"}}");
        Files.write(root.resolve(path), bytes); return root;
    }
    private void zip(String name, Map<String,byte[]> entries) throws IOException {
        try(ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(packs.resolve(name)))) {
            for(var entry:entries.entrySet()) { out.putNextEntry(new ZipEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry(); }
        }
    }
    private static JsonObject metadata() { JsonObject o=new JsonObject();o.addProperty("pack_format",46);return o; }

    @Test void favoritesAndSelectionsSurviveRestartAndRemovedSources() throws Exception {
        byte[] original = png(0xff21adca);
        Path source = folder("Ocean", SWORD, original);
        Files.writeString(source.resolve(SWORD+".mcmeta"), "{\"animation\":{\"frametime\":2}}");
        TextureRef ref = catalog.scan().textures().getFirst();
        LibraryStore library = new LibraryStore(temp.resolve("config"));
        library.toggleFavorite(ref,catalog); library.select(ref,catalog); library.rename("Mein PvP-Pack");
        Files.delete(source.resolve(SWORD)); Files.delete(source.resolve(SWORD+".mcmeta"));
        LibraryStore reopened = new LibraryStore(temp.resolve("config"));
        assertEquals("Mein PvP-Pack",reopened.state().name());
        assertEquals(1,reopened.state().favorites().size()); assertEquals(1,reopened.state().selected().size());
        assertArrayEquals(original,reopened.read(reopened.state().favorites().get(ref.key())).png());
        Path exported = PackExporter.export(reopened,packs,metadata());
        try(ZipFile zip = new ZipFile(exported.toFile())) {
            assertArrayEquals(original,zip.getInputStream(zip.getEntry(SWORD)).readAllBytes());
            assertNotNull(zip.getEntry(SWORD+".mcmeta"));
            JsonObject meta = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(46,meta.getAsJsonObject("pack").get("pack_format").getAsInt());
            assertTrue(meta.getAsJsonObject("pack").get("description").getAsString().contains("Mein PvP-Pack"));
        }
    }
    @Test void laterChoiceReplacesSameResourceButKeepsBothFavorites() throws Exception {
        folder("A",SWORD,png(0xff11aaaa)); folder("B",SWORD,png(0xffcc1144));
        var refs=catalog.scan().textures(); LibraryStore store=new LibraryStore(temp.resolve("config"));
        for(var ref:refs) { store.toggleFavorite(ref,catalog); store.select(ref,catalog); }
        assertEquals(2,store.state().favorites().size()); assertEquals(1,store.state().selected().size());
        assertEquals("B",store.state().selected().get(SWORD).pack());
        store.remove(SWORD); assertTrue(store.state().selected().isEmpty()); assertEquals(2,store.state().favorites().size());
    }
    @Test void scansFolderAndZipAndRejectsTraversalAndNestedZipRoot() throws Exception {
        folder("Folder",SWORD,png(0xff12ab34));
        zip("Archive.zip",Map.of("pack.mcmeta","{}".getBytes(),SWORD,png(0xff123456),"assets/minecraft/textures/../../outside.png",png(0xffabcdef)));
        zip("Nested.zip",Map.of("nested/pack.mcmeta","{}".getBytes(),"nested/"+SWORD,png(0xff000000)));
        var scan=catalog.scan();assertEquals(2,scan.textures().size());assertEquals(1,scan.warnings().size());
        assertArrayEquals(png(0xff123456),catalog.read(scan.textures().stream().filter(t->t.pack().equals("Archive.zip")).findFirst().orElseThrow()).png());
        assertThrows(IllegalArgumentException.class,()->new TextureRef("../bad",SWORD,""));
        assertThrows(IllegalArgumentException.class,()->new TextureRef("okay","assets/a/textures/../x.png",""));
        assertThrows(IllegalArgumentException.class,()->new TextureRef("okay",SWORD,"../x"));
        assertFalse(Files.exists(temp.resolve("outside.png")));
    }
    @Test void damagedStateRecoversBackupWithoutLosingItOnNextWrite() throws Exception {
        LibraryStore store = new LibraryStore(temp.resolve("config")); store.rename("Erstes Pack"); store.rename("Zweites Pack");
        Files.writeString(store.root().resolve("library.json"), "{broken");
        LibraryStore recovered = new LibraryStore(store.root()); assertEquals("Erstes Pack",recovered.state().name());
        assertFalse(recovered.recoveryMessage().isEmpty()); recovered.rename("Gerettet");
        assertEquals("Gerettet",new LibraryStore(store.root()).state().name());
        try(var files=Files.list(store.root())) { assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("library-damaged-"))); }
    }
    @Test void refusesToOverwriteUnrecoverableState() throws Exception {
        Path root=temp.resolve("config");Files.createDirectories(root);Path file=root.resolve("library.json");Files.writeString(file,"BROKEN");
        assertThrows(IOException.class,()->new LibraryStore(root));assertEquals("BROKEN",Files.readString(file));
    }
    @Test void corruptSavedTextureCannotReplacePreviousExport() throws Exception {
        folder("Pack",SWORD,png(0xffab1234)); LibraryStore store=new LibraryStore(temp.resolve("config"));
        store.select(catalog.scan().textures().getFirst(),catalog);
        Path export=PackExporter.export(store,packs,metadata());byte[] original=Files.readAllBytes(export);
        String blob=store.state().selected().get(SWORD).blob();
        Files.write(store.root().resolve("textures").resolve(blob).resolve("texture.png"),new byte[]{1,2,3});
        assertThrows(IOException.class,()->PackExporter.export(store,packs,metadata()));assertArrayEquals(original,Files.readAllBytes(export));
        try(var files=Files.list(packs)) {assertFalse(files.anyMatch(p->p.toString().endsWith(".tmp")));}
    }
    @Test void malformedPngDoesNotEnterFavoritesOrSelection() throws Exception {
        folder("Bad",SWORD,new byte[]{1,2,3});LibraryStore store=new LibraryStore(temp.resolve("config"));TextureRef ref=catalog.scan().textures().getFirst();
        assertThrows(IOException.class,()->store.select(ref,catalog));assertTrue(store.state().selected().isEmpty());
        assertThrows(IOException.class,()->store.toggleFavorite(ref,catalog));assertTrue(store.state().favorites().isEmpty());
    }
    @Test void honorsFirstAnimationFrameAndPreservesSheet() throws Exception {
        BufferedImage sheet = new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++) for(int x=0;x<16;x++) sheet.setRGB(x,y,y<16?0xffff0000:0xff00ff00);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(sheet,"png",out);
        TextureImage decoded=TextureImage.decode(new PackCatalog.Data(out.toByteArray(),"{\"animation\":{\"frames\":[1,0]}}".getBytes()));
        assertTrue(decoded.animated());assertEquals(16,decoded.frame().getHeight());assertEquals(32,decoded.height());assertEquals(0xff00ff00,decoded.frame().getRGB(0,0));
        assertThrows(IOException.class,()->TextureImage.decode(new PackCatalog.Data(out.toByteArray(),"{\"animation\":{\"width\":0}}".getBytes())));
    }
    @Test void searchesAcrossNamePathAndPackWithMultipleTerms() {
        TextureRef ref=new TextureRef("PvP Blue.zip",SWORD,"");
        assertTrue(ref.matches("DIAMOND blue"));assertTrue(ref.matches("minecraft sword"));assertFalse(ref.matches("red sword"));
    }
    @Test void zipBombReadIsBounded() throws Exception {
        byte[] oversized=new byte[33*1024*1024];
        zip("Big.zip",Map.of("pack.mcmeta","{}".getBytes(),SWORD,oversized));
        assertTrue(catalog.scan().textures().isEmpty());
        assertThrows(IOException.class,()->catalog.read(new TextureRef("Big.zip",SWORD,"")));
    }
    @Test void changedExportsUseNewFilesAndDoNotRescanAsSources() throws Exception {
        folder("A",SWORD,png(0xff11aaaa));folder("B",SWORD,png(0xffcc1144));
        var refs=catalog.scan().textures();LibraryStore store=new LibraryStore(temp.resolve("config"));
        store.select(refs.get(0),catalog);Path first=PackExporter.export(store,packs,metadata());
        store.toggleFavorite(refs.get(0),catalog);assertEquals(first,PackExporter.export(store,packs,metadata()));
        byte[] before=Files.readAllBytes(first);store.select(refs.get(1),catalog);Path second=PackExporter.export(store,packs,metadata());
        assertNotEquals(first,second);assertArrayEquals(before,Files.readAllBytes(first));
        assertEquals(2,catalog.scan().textures().size());assertEquals(2,catalog.scan().packs().size());
    }
    @Test void modernExportKeepsExactMajorAndMinorFormat() throws Exception {
        folder("Pack",SWORD,png(0xff123456));LibraryStore store=new LibraryStore(temp.resolve("config"));store.select(catalog.scan().textures().getFirst(),catalog);
        JsonObject format=JsonParser.parseString("{\"min_format\":[97,1],\"max_format\":[97,1]}").getAsJsonObject();
        Path output=PackExporter.export(store,packs,format);
        try(ZipFile zip=new ZipFile(output.toFile())) {
            JsonObject pack=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
            assertEquals(format.get("min_format"),pack.get("min_format"));assertEquals(format.get("max_format"),pack.get("max_format"));
        }
    }
}
