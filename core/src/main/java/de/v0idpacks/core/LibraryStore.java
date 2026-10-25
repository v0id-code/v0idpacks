package de.v0idpacks.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All writes are atomic and write-through. A returned mutation has reached disk. */
public final class LibraryStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public record State(int schema, String name, Map<String, TextureRef> favorites, Map<String, TextureRef> selected) {
        public State {
            if (schema != 1 || name == null || name.isBlank() || name.length() > 64 || favorites == null || selected == null)
                throw new IllegalArgumentException("Ungültige Projektdatei");
            favorites = Collections.unmodifiableMap(new LinkedHashMap<>(favorites));
            selected = Collections.unmodifiableMap(new TreeMap<>(selected));
            if (favorites.size() + selected.size() > PackCatalog.MAX_ENTRIES) throw new IllegalArgumentException("Projekt zu groß");
            favorites.forEach((key, value) -> { if (value == null || value.blob().isEmpty() || !key.equals(value.key())) throw new IllegalArgumentException("Ungültiger Favorit"); });
            selected.forEach((key, value) -> { if (value == null || value.blob().isEmpty() || !key.equals(value.path())) throw new IllegalArgumentException("Ungültige Auswahl"); });
        }
    }
    private final Path root;
    private final Path statePath;
    private volatile State state;
    private String recoveryMessage = "";
    public LibraryStore(Path root) throws IOException {
        this.root = root.toAbsolutePath();
        Files.createDirectories(this.root.resolve("textures"));
        statePath = this.root.resolve("library.json");
        state = new State(1, "Mein v0idpack", Map.of(), Map.of());
        if (Files.exists(statePath)) {
            try { state = load(statePath); }
            catch (IOException | RuntimeException ex) {
                Path backup = this.root.resolve("library.backup.json");
                try {
                    state = load(backup);
                    Files.copy(statePath, this.root.resolve("library-damaged-" + System.currentTimeMillis() + ".json"));
                    // Repair primary first so a subsequent save cannot replace the good backup with damaged JSON.
                    SafeFiles.atomicWrite(statePath, JSON.toJson(state).getBytes(StandardCharsets.UTF_8));
                    recoveryMessage = "Projekt aus Sicherung wiederhergestellt. Beschädigte Datei wurde aufbewahrt.";
                } catch (IOException | RuntimeException recovery) { throw new IOException("Projektdatei kann nicht gelesen werden: " + statePath, ex); }
            }
        }
    }
    private State load(Path path) throws IOException {
        State loaded = JSON.fromJson(new String(SafeFiles.read(path, 16 * 1024 * 1024), StandardCharsets.UTF_8), State.class);
        if (loaded == null) throw new IOException("Leere Projektdatei");
        return loaded;
    }
    public State state() { return state; }
    public String recoveryMessage() { return recoveryMessage; }
    public Path root() { return root; }

    public synchronized TextureRef snapshot(TextureRef ref, PackCatalog catalog) throws IOException {
        if (!ref.blob().isEmpty()) { read(ref); return ref; }
        PackCatalog.Data data = catalog.read(ref);
        TextureImage.decode(data); // Validate before committing a persistent copy.
        String hash = SafeFiles.hash(data.png(), data.metadata());
        Path dir = root.resolve("textures").resolve(hash);
        Files.createDirectories(dir);
        SafeFiles.atomicWrite(dir.resolve("texture.png"), data.png());
        if (data.metadata().length > 0) SafeFiles.atomicWrite(dir.resolve("texture.png.mcmeta"), data.metadata());
        return new TextureRef(ref.pack(), ref.path(), hash);
    }
    public PackCatalog.Data read(TextureRef ref) throws IOException {
        if (ref.blob().isEmpty()) throw new IOException("Keine gespeicherte Kopie");
        Path dir = root.resolve("textures").resolve(ref.blob());
        byte[] png = SafeFiles.read(dir.resolve("texture.png"), SafeFiles.MAX_TEXTURE);
        Path metadata = dir.resolve("texture.png.mcmeta");
        byte[] meta = Files.exists(metadata) ? SafeFiles.read(metadata, SafeFiles.MAX_METADATA) : new byte[0];
        if (!SafeFiles.hash(png, meta).equals(ref.blob())) throw new IOException("Gespeicherte Textur beschädigt: " + ref.name());
        return new PackCatalog.Data(png, meta);
    }
    public synchronized void toggleFavorite(TextureRef ref, PackCatalog catalog) throws IOException {
        Map<String, TextureRef> favorites = new LinkedHashMap<>(state.favorites());
        if (favorites.containsKey(ref.key())) favorites.remove(ref.key());
        else favorites.put(ref.key(), snapshot(ref, catalog));
        commit(new State(1, state.name(), favorites, state.selected()));
    }
    public synchronized void select(TextureRef ref, PackCatalog catalog) throws IOException {
        Map<String, TextureRef> selected = new TreeMap<>(state.selected());
        selected.put(ref.path(), snapshot(ref, catalog));
        commit(new State(1, state.name(), state.favorites(), selected));
    }
    public synchronized void remove(String path) throws IOException {
        Map<String, TextureRef> selected = new TreeMap<>(state.selected());
        selected.remove(path);
        commit(new State(1, state.name(), state.favorites(), selected));
    }
    public synchronized void rename(String name) throws IOException {
        commit(new State(1, name.strip(), state.favorites(), state.selected()));
    }
    private void commit(State next) throws IOException {
        if (Files.exists(statePath)) SafeFiles.atomicWrite(root.resolve("library.backup.json"), SafeFiles.read(statePath, 16 * 1024 * 1024));
        SafeFiles.atomicWrite(statePath, JSON.toJson(next).getBytes(StandardCharsets.UTF_8));
        state = next;
    }
}
