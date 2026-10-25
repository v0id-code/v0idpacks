package de.v0idpacks.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class UiPreferencesTest {
    @TempDir Path temp;
    @Test void defaultsAndChangesSurviveRestartWithoutChangingLibrary() throws Exception {
        LibraryStore library=new LibraryStore(temp);library.rename("Mein eigenes Pack");
        UiPreferences prefs=new UiPreferences(temp);assertTrue(prefs.animationsEnabled());
        prefs.setAnimationsEnabled(false);assertFalse(new UiPreferences(temp).animationsEnabled());
        assertEquals("Mein eigenes Pack",new LibraryStore(temp).state().name());
        prefs.setAnimationsEnabled(true);assertTrue(new UiPreferences(temp).animationsEnabled());
    }
    @Test void invalidPreferenceFallsBackWithoutDiscardingLibrary() throws Exception {
        for(String value:new String[]{"broken", "[]", "{\"animations\":\"false\"}", "{\"animations\":null}"}) {
            Files.writeString(temp.resolve("ui.json"),value);
            UiPreferences prefs=new UiPreferences(temp);
            assertTrue(prefs.animationsEnabled());assertFalse(prefs.warning().isEmpty());
            prefs.setAnimationsEnabled(false);assertFalse(new UiPreferences(temp).animationsEnabled());
        }
    }
    @Test void failedWriteDoesNotClaimThatPreferenceWasSaved() throws Exception {
        UiPreferences prefs=new UiPreferences(temp);
        Files.createDirectory(temp.resolve("ui.json"));Files.writeString(temp.resolve("ui.json/blocker"),"occupied");
        assertThrows(java.io.IOException.class,()->prefs.setAnimationsEnabled(false));
        assertTrue(prefs.animationsEnabled());
    }
}
