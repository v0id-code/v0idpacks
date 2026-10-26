package de.v0idpacks.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All writes are atomic and write-through. A returned mutation has reached disk. */
public final class LibraryStore {
    public static final String LEGACY_PROJECT_ID = "00000000000000000000000000000000";
    private static final int MAX_LIBRARY_BYTES = 16 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public record State(int schema, String name, Map<String, TextureRef> favorites, Map<String, TextureRef> selected, String logo) {
        public State(int schema, String name, Map<String, TextureRef> favorites, Map<String, TextureRef> selected) {
            this(schema, name, favorites, selected, "");
        }
        public State {
            logo = logo == null ? "" : logo;
            if (!logo.isEmpty() && !logo.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Ungültiges Pack-Logo");
            if (schema != 1 || name == null || name.isBlank() || name.length() > 64 || favorites == null || selected == null)
                throw new IllegalArgumentException("Ungültige Projektdatei");
            favorites = Collections.unmodifiableMap(new LinkedHashMap<>(favorites));
            selected = Collections.unmodifiableMap(new TreeMap<>(selected));
            if (favorites.size() + selected.size() > PackCatalog.MAX_ENTRIES) throw new IllegalArgumentException("Projekt zu groß");
            favorites.forEach((key, value) -> { if (value == null || value.blob().isEmpty() || !key.equals(value.key())) throw new IllegalArgumentException("Ungültiger Favorit"); });
            selected.forEach((key, value) -> { if (value == null || value.blob().isEmpty() || !key.equals(value.path())) throw new IllegalArgumentException("Ungültige Auswahl"); });
        }
    }
    public record Project(String name, Map<String, TextureRef> selected, String logo) {
        public Project {
            State validated = new State(1, name, Map.of(), selected, logo);
            selected = validated.selected(); logo = validated.logo();
        }
    }
    private record Workspace(int schema, String activeProject, Map<String, TextureRef> favorites, Map<String, Project> projects) {
        private Workspace {
            if (schema != 2 || projects == null || projects.isEmpty() || projects.size() > 1000 || !projects.containsKey(activeProject))
                throw new IllegalArgumentException("Ungültige Pack-Projekte");
            projects.forEach((id, project) -> {
                if (id == null || !id.matches("[a-f0-9]{32}") || project == null) throw new IllegalArgumentException("Ungültiges Pack-Projekt");
            });
            favorites = new State(1, "Favoriten", favorites, Map.of()).favorites();
            if (favorites.size() + projects.values().stream().mapToLong(p -> p.selected().size()).sum() > PackCatalog.MAX_ENTRIES)
                throw new IllegalArgumentException("Bibliothek zu groß");
            projects = Collections.unmodifiableMap(new LinkedHashMap<>(projects));
        }
    }
    private record Snapshot(Workspace workspace, State state) {
        private Snapshot(Workspace workspace) {
            this(workspace, new State(1, workspace.projects().get(workspace.activeProject()).name(), workspace.favorites(),
                    workspace.projects().get(workspace.activeProject()).selected(), workspace.projects().get(workspace.activeProject()).logo()));
        }
    }
    private final Path root;
    private final Path statePath;
    private volatile Snapshot snapshot;
    private String recoveryMessage = "";
    public LibraryStore(Path root) throws IOException {
        this.root = root.toAbsolutePath();
        Files.createDirectories(this.root.resolve("textures"));
        statePath = this.root.resolve("library.json");
        snapshot = new Snapshot(migrate(new State(1, "Mein v0idpack", Map.of(), Map.of())));
        if (Files.exists(statePath)) {
            try { snapshot = new Snapshot(load(statePath)); }
            catch (IOException | RuntimeException ex) {
                Path backup = this.root.resolve("library.backup.json");
                try {
                    snapshot = new Snapshot(load(backup));
                    Files.copy(statePath, this.root.resolve("library-damaged-" + System.currentTimeMillis() + ".json"));
                    // Repair primary first so a subsequent save cannot replace the good backup with damaged JSON.
                    SafeFiles.atomicWrite(statePath, JSON.toJson(snapshot.workspace()).getBytes(StandardCharsets.UTF_8));
                    recoveryMessage = "Projekt aus Sicherung wiederhergestellt. Beschädigte Datei wurde aufbewahrt.";
                } catch (IOException | RuntimeException recovery) { throw new IOException("Projektdatei kann nicht gelesen werden: " + statePath, ex); }
            }
        }
    }
    private static Workspace migrate(State old) {
        return new Workspace(2, LEGACY_PROJECT_ID, old.favorites(), Map.of(LEGACY_PROJECT_ID, new Project(old.name(), old.selected(), old.logo())));
    }
    private Workspace load(Path path) throws IOException {
        JsonObject json = JsonParser.parseString(new String(SafeFiles.read(path, MAX_LIBRARY_BYTES), StandardCharsets.UTF_8)).getAsJsonObject();
        return json.get("schema").getAsInt() == 1 ? migrate(JSON.fromJson(json, State.class)) : JSON.fromJson(json, Workspace.class);
    }
    public State state() { return snapshot.state(); }
    public String projectId() { return snapshot.workspace().activeProject(); }
    public Map<String, Project> projects() { return snapshot.workspace().projects(); }
    public synchronized String createProject(String name) throws IOException { return createProject(name, state().name()); }
    public synchronized String createProject(String name, String currentName) throws IOException {
        Map<String, Project> projects = projectsWithCurrentName(currentName);
        String id = UUID.randomUUID().toString().replace("-", "");
        projects.put(id, new Project(name.strip(), Map.of(), ""));
        commit(new Workspace(2, id, state().favorites(), projects));
        return id;
    }
    public synchronized void switchProject(String id) throws IOException { switchProject(id, state().name()); }
    public synchronized void switchProject(String id, String currentName) throws IOException {
        if (!projects().containsKey(id)) throw new IOException("Pack-Projekt nicht gefunden");
        commit(new Workspace(2, id, state().favorites(), projectsWithCurrentName(currentName)));
    }
    private Map<String, Project> projectsWithCurrentName(String name) {
        Map<String, Project> projects = new LinkedHashMap<>(projects());
        projects.put(projectId(), new Project(name.strip(), state().selected(), state().logo()));
        return projects;
    }
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
        State state = state();
        Map<String, TextureRef> favorites = new LinkedHashMap<>(state.favorites());
        if (favorites.containsKey(ref.key())) favorites.remove(ref.key());
        else favorites.put(ref.key(), snapshot(ref, catalog));
        commit(new State(1, state.name(), favorites, state.selected(), state.logo()));
    }
    public synchronized void select(TextureRef ref, PackCatalog catalog) throws IOException {
        State state = state();
        Map<String, TextureRef> selected = new TreeMap<>(state.selected());
        if (CrystalTextures.contains(ref.path())) {
            String companion = CrystalTextures.companion(ref.path());
            if (catalog.readAsset(ref.pack(), companion) == null)
                throw new IOException("End Crystal: " + companion + " fehlt im Quell-Pack. Inventar und Entity müssen gemeinsam aus demselben Pack übernommen werden.");
            // Validate and snapshot both files before committing either replacement.
            TextureRef other = snapshot(new TextureRef(ref.pack(), companion, ""), catalog);
            selected.put(companion, other);
        }
        selected.put(ref.path(), snapshot(ref, catalog));
        commit(new State(1, state.name(), state.favorites(), selected, state.logo()));
    }
    public synchronized void remove(String path) throws IOException {
        State state = state();
        Map<String, TextureRef> selected = new TreeMap<>(state.selected());
        selected.remove(path);
        if (CrystalTextures.contains(path)) selected.remove(CrystalTextures.companion(path));
        commit(new State(1, state.name(), state.favorites(), selected, state.logo()));
    }
    public synchronized void rename(String name) throws IOException {
        State state = state();
        commit(new State(1, name.strip(), state.favorites(), state.selected(), state.logo()));
    }
    public synchronized void importLogo(Path file) throws IOException {
        State state = state();
        byte[] png = SafeFiles.read(file, SafeFiles.MAX_TEXTURE);
        TextureImage image = TextureImage.decode(new PackCatalog.Data(png, new byte[0]));
        if (image.width() != image.height()) throw new IOException("Das Pack-Logo muss ein quadratisches PNG sein.");
        String hash = SafeFiles.hash(png, new byte[0]);
        SafeFiles.atomicWrite(root.resolve("logos").resolve(hash + ".png"), png);
        commit(new State(1, state.name(), state.favorites(), state.selected(), hash));
    }
    public synchronized void removeLogo() throws IOException {
        State state = state();
        commit(new State(1, state.name(), state.favorites(), state.selected(), ""));
    }
    public byte[] readLogo(State snapshot) throws IOException {
        if (snapshot.logo().isEmpty()) return new byte[0];
        byte[] png = SafeFiles.read(root.resolve("logos").resolve(snapshot.logo() + ".png"), SafeFiles.MAX_TEXTURE);
        if (!SafeFiles.hash(png, new byte[0]).equals(snapshot.logo())) throw new IOException("Gespeichertes Pack-Logo beschädigt");
        return png;
    }
    private void commit(State next) throws IOException {
        Map<String, Project> projects = new LinkedHashMap<>(projects());
        projects.put(projectId(), new Project(next.name(), next.selected(), next.logo()));
        commit(new Workspace(2, projectId(), next.favorites(), projects));
    }
    private void commit(Workspace next) throws IOException {
        Snapshot ready = new Snapshot(next);
        byte[] bytes = JSON.toJson(next).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_LIBRARY_BYTES) throw new IOException("Bibliothek überschreitet das Größenlimit");
        if (Files.exists(statePath)) SafeFiles.atomicWrite(root.resolve("library.backup.json"), SafeFiles.read(statePath, MAX_LIBRARY_BYTES));
        SafeFiles.atomicWrite(statePath, bytes);
        snapshot = ready;
    }
}
