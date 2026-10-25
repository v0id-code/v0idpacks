package de.v0idpacks.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiProjectTest {
    @TempDir Path temp;
    private static final String TEXTURE = "assets/minecraft/textures/item/diamond.png";
    private TextureRef source(String pack, int color) throws Exception {
        Path folder = temp.resolve("packs").resolve(pack);
        Files.createDirectories(folder.resolve(TEXTURE).getParent());
        Files.writeString(folder.resolve("pack.mcmeta"), "{}");
        Files.write(folder.resolve(TEXTURE), LibraryIntegrationTest.png(color));
        return new TextureRef(pack,TEXTURE,"");
    }
    private Path export(LibraryStore store) throws Exception {
        JsonObject format = new JsonObject(); format.addProperty("pack_format",46);
        return PackExporter.export(store,temp.resolve("packs"),format);
    }
    @Test void independentProjectsRemainEditableAfterRestartAndSourceRemoval() throws Exception {
        LibraryStore store = new LibraryStore(temp.resolve("config"));
        PackCatalog catalog = new PackCatalog(temp.resolve("packs"));
        TextureRef blue = source("Blue",0xff0000ff), red = source("Red",0xffff0000);
        String first = store.projectId(); store.select(blue,catalog); store.toggleFavorite(blue,catalog);
        Path logo = temp.resolve("logo.png"); Files.write(logo,LibraryIntegrationTest.png(0xff123456)); store.importLogo(logo);
        String second = store.createProject("Zweites Pack","Blaues Pack");
        assertTrue(store.state().selected().isEmpty()); assertTrue(store.state().logo().isEmpty());
        assertEquals(1,store.state().favorites().size()); store.select(red,catalog);
        store = new LibraryStore(store.root()); assertEquals(second,store.projectId()); assertEquals(2,store.projects().size());
        Files.delete(temp.resolve("packs/Blue").resolve(TEXTURE)); Files.delete(logo);
        store.switchProject(first,"Rotes Pack");
        assertEquals("Blaues Pack",store.state().name()); assertEquals("Blue",store.state().selected().get(TEXTURE).pack());
        assertTrue(store.readLogo(store.state()).length>0); assertTrue(Files.exists(export(store)));
        store.remove(TEXTURE); store.removeLogo(); store.rename("Blau überarbeitet");
        store.switchProject(second);
        assertEquals("Rotes Pack",store.state().name()); assertEquals("Red",store.state().selected().get(TEXTURE).pack());
        assertEquals(1,store.state().favorites().size()); assertTrue(store.state().logo().isEmpty());
        store.switchProject(first); assertTrue(store.state().selected().isEmpty()); assertEquals("Blau überarbeitet",store.state().name());
    }
    @Test void sameNamesAndTexturesHaveSeparateExportsAndActivationOwnership() throws Exception {
        LibraryStore store=new LibraryStore(temp.resolve("config")); PackCatalog catalog=new PackCatalog(temp.resolve("packs"));
        TextureRef texture=source("Source",0xffaabbcc);
        String first=store.projectId(); store.rename("Mein Pack");store.select(texture,catalog);Path old=export(store);
        String second=store.createProject("Mein Pack");store.select(texture,catalog);Path other=export(store);
        assertNotEquals(old,other);store.switchProject(first);store.rename("Neuer Name");Path updated=export(store);
        assertTrue(PackPresentation.belongsToProject(updated.getParent(),"file/"+old.getFileName(),first));
        assertFalse(PackPresentation.belongsToProject(updated.getParent(),"file/"+other.getFileName(),first));
        assertEquals(second,PackPresentation.readProjectId(other.getParent(),"file/"+other.getFileName()));
        assertEquals(1,catalog.scan().packs().size());
        assertFalse(PackPresentation.belongsToProject(updated.getParent(),"vanilla",first));
    }
    @Test void migrationAndBackupPreserveAllProjects() throws Exception {
        Path root=temp.resolve("config"); Files.createDirectories(root);
        Files.writeString(root.resolve("library.json"),"{\"schema\":1,\"name\":\"Vorhandenes Pack\",\"favorites\":{},\"selected\":{}}");
        LibraryStore store=new LibraryStore(root);String first=store.projectId();
        assertEquals(LibraryStore.LEGACY_PROJECT_ID,first);
        String second=store.createProject("Weiteres Pack");store.rename("Umbenannt");
        Files.writeString(root.resolve("library.json"),"broken");
        LibraryStore restored=new LibraryStore(root);
        assertEquals(Set.of(first,second),restored.projects().keySet());assertEquals(second,restored.projectId());
        assertEquals("Weiteres Pack",restored.state().name());restored.switchProject(first);
        assertEquals("Vorhandenes Pack",restored.state().name());
    }
    @Test void failedSwitchOrCreateCannotLoseCurrentEdits() throws Exception {
        LibraryStore store=new LibraryStore(temp.resolve("config"));store.rename("Bleibt erhalten");String first=store.projectId();
        byte[] before=Files.readAllBytes(store.root().resolve("library.json"));
        assertThrows(java.io.IOException.class,()->store.switchProject("missing","Verloren"));
        assertThrows(IllegalArgumentException.class,()->store.createProject("","Verloren"));
        assertThrows(IllegalArgumentException.class,()->store.createProject("Neu",""));
        assertEquals(first,store.projectId());assertEquals("Bleibt erhalten",store.state().name());
        assertEquals(1,store.projects().size());assertArrayEquals(before,Files.readAllBytes(store.root().resolve("library.json")));
    }
}
