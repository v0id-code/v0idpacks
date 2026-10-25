package de.v0idpacks.ui;

import de.v0idpacks.core.*;
import com.google.gson.JsonObject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One session per game; disk work is serialized away from the render thread. */
public final class EditorSession implements AutoCloseable {
    public enum Tab { ALL, FAVORITES, SELECTED }
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "v0idpacks-io"); t.setDaemon(true); return t; });
    private final ConcurrentLinkedQueue<Runnable> completed = new ConcurrentLinkedQueue<>();
    private final Set<String> requested = new HashSet<>();
    private final LinkedHashMap<String, BufferedImage> thumbnails = new LinkedHashMap<>(64, .75f, true);
    private final Map<String, String> imageErrors = new HashMap<>();
    private PackCatalog catalog;
    private LibraryStore library;
    private BufferedImage logo;
    private UiPreferences preferences;
    private PackCatalog.Scan scan = new PackCatalog.Scan(List.of(), List.of(), List.of());
    private final JsonObject packMetadata;
    private final Consumer<Path> activate;
    private final ModelLoader.Assets fallbackAssets;
    private long revision;
    private long previewGeneration;
    private int scanGeneration;
    private boolean busy = true;
    private boolean fatal;
    private String status = "Installierte Packs werden gelesen…";
    private boolean error;
    private TextureRef focused;
    private TextureImage preview;
    private PreviewModel previewModel;
    private String modelNote="";
    private String previewError = "";
    private Tab tab = Tab.ALL;
    private TextureCategory categoryFilter = TextureCategory.ALL;
    private ArmorPart armorPartFilter = ArmorPart.ALL;
    private ToolType toolTypeFilter = ToolType.ALL;
    private String query = "", packFilter = "";
    private List<TextureRef> filtered = List.of();

    public EditorSession(Path resourcepacks, Path config, JsonObject packMetadata, Consumer<Path> activate) {
        this(resourcepacks,config,packMetadata,activate,path->null);
    }
    public EditorSession(Path resourcepacks, Path config, JsonObject packMetadata, Consumer<Path> activate,ModelLoader.Assets fallbackAssets) {
        this.packMetadata = packMetadata.deepCopy(); this.activate = activate;this.fallbackAssets=fallbackAssets;
        io.execute(() -> {
            try {
                PackCatalog c = new PackCatalog(resourcepacks);
                LibraryStore l = new LibraryStore(config);
                BufferedImage savedLogo = null;
                String logoWarning = "";
                try { savedLogo = decodeLogo(l); }
                catch (IOException e) { logoWarning = "Pack-Logo nicht lesbar. Bitte ersetzen oder entfernen."; }
                BufferedImage initialLogo = savedLogo;
                String initialLogoWarning = logoWarning;
                UiPreferences prefs=new UiPreferences(config);
                PackCatalog.Scan result = c.scan();
                completed.add(() -> {
                    catalog = c; library = l; preferences=prefs; scan = result; busy = false;
                    logo = initialLogo;
                    message(!l.recoveryMessage().isEmpty()?l.recoveryMessage():!prefs.warning().isEmpty()?prefs.warning():scanMessage(), !result.warnings().isEmpty()||!prefs.warning().isEmpty());
                    refilter();
                    if (!initialLogoWarning.isEmpty()) message(initialLogoWarning, true);
                });
            } catch (Exception e) { completed.add(() -> { busy = false; fatal = true; message("Bibliothek nicht verfügbar: " + reason(e), true); }); }
        });
    }
    public void poll() { Runnable task; while ((task = completed.poll()) != null) task.run(); }
    public long revision() { return revision; }
    public boolean busy() { return busy; }
    public boolean ready() { return library != null && !fatal; }
    public boolean animationsEnabled(){return preferences==null||preferences.animationsEnabled();}
    public void toggleAnimations(){
        boolean enabled=!animationsEnabled();
        run("Animationseinstellung wird gespeichert…",()->preferences.setAnimationsEnabled(enabled),enabled?"Animationen eingeschaltet.":"Animationen ausgeschaltet.");
    }
    public String status() { return status; }
    public boolean error() { return error; }
    public Tab tab() { return tab; }
    public String query() { return query; }
    public String packFilter() { return packFilter; }
    public TextureCategory categoryFilter() { return categoryFilter; }
    public ArmorPart armorPartFilter() { return armorPartFilter; }
    public ToolType toolTypeFilter() { return toolTypeFilter; }
    public List<PackInfo> packs() { return scan.packs(); }
    public List<PackInfo> visiblePacks() { return scan.packs().stream().filter(p->p.matches(query)).toList(); }
    public String packName(String id) { return scan.packs().stream().filter(p->p.id().equals(id)).map(PackInfo::displayName).findFirst().orElse(id); }
    public List<String> warnings() { return scan.warnings(); }
    public List<TextureRef> visible() { return filtered; }
    public TextureRef focused() { return focused; }
    public TextureImage preview() { return preview; }
    public PreviewModel previewModel() {return previewModel;}
    public String modelNote() {return modelNote;}
    public String previewError() { return previewError; }
    public int favoriteCount() { return ready() ? library.state().favorites().size() : 0; }
    public int selectedCount() { return ready() ? library.state().selected().size() : 0; }
    public String name() { return ready() ? library.state().name() : "Mein v0idpack"; }
    public String projectId() { return ready() ? library.projectId() : ""; }
    public Map<String, LibraryStore.Project> projects() { return ready() ? library.projects() : Map.of(); }
    public void createProject(String currentName) { changeProject(null, currentName); }
    public void switchProject(String id, String currentName) { changeProject(id, currentName); }
    private void changeProject(String id, String currentName) {
        if (!ready() || busy) return;
        if (currentName.strip().isEmpty()) { message("Bitte zuerst einen Pack-Namen eingeben.", true); return; }
        busy = true; message(id == null ? "Neues Pack wird angelegt…" : "Pack wird geöffnet…", false);
        io.execute(() -> {
            try {
                if (id == null) {
                    int number = library.projects().size() + 1;
                    Set<String> names = new HashSet<>(); library.projects().values().forEach(p -> names.add(p.name()));
                    String name; do { name = "Mein v0idpack " + number++; } while (names.contains(name));
                    library.createProject(name, currentName);
                } else library.switchProject(id, currentName);
                BufferedImage loaded = null;
                String warning = "";
                try { loaded = decodeLogo(library); }
                catch (IOException e) { warning = "Pack geöffnet. Logo nicht lesbar; bitte ersetzen oder entfernen."; }
                BufferedImage nextLogo = loaded; String logoWarning = warning;
                completed.add(() -> {
                    logo = nextLogo; busy = false; focus(null);
                    filter(Tab.SELECTED, "", "");
                    message(logoWarning.isEmpty() ? "„" + name() + "“ zum Bearbeiten geöffnet. Änderungen werden lokal gespeichert." : logoWarning, !logoWarning.isEmpty());
                });
            } catch (Exception e) { completed.add(() -> { busy = false; message(reason(e), true); }); }
        });
    }
    public BufferedImage logo() { return logo; }
    public boolean hasLogo() { return ready() && !library.state().logo().isEmpty(); }
    private static BufferedImage decodeLogo(LibraryStore store) throws IOException {
        byte[] png = store.readLogo(store.state());
        return png.length == 0 ? null : TextureImage.decode(new PackCatalog.Data(png, new byte[0])).frame();
    }
    public void importLogo(Path file) {
        importLogo(file, projectId());
    }
    public void importLogo(Path file, String expectedProject) {
        if (!projectId().equals(expectedProject)) { message("Pack wurde gewechselt. Bitte das Logo erneut auswählen.", true); return; }
        run("Logo wird gespeichert…", () -> {
            library.importLogo(file);
            BufferedImage image = decodeLogo(library);
            completed.add(() -> logo = image);
        }, "Pack-Logo gespeichert. Beim nächsten Export wird es übernommen.");
    }
    public void removeLogo() {
        run("Logo wird entfernt…", () -> { library.removeLogo(); completed.add(() -> logo = null); }, "Pack-Logo entfernt.");
    }
    public void logoSelectionFailed(String reason) { message("Logo konnte nicht ausgewählt werden: " + reason, true); }
    public boolean isFavorite(TextureRef ref) { return ready() && library.state().favorites().containsKey(ref.key()); }
    public boolean isSelected(TextureRef ref) {
        if (!ready()) return false;
        TextureRef selection = library.state().selected().get(ref.path());
        return selection != null && selection.key().equals(ref.key());
    }
    public boolean replacesOther(TextureRef ref) {
        return ready() && library.state().selected().containsKey(ref.path()) && !isSelected(ref);
    }
    public void filter(Tab tab, String query, String pack) {
        this.tab = tab; this.query = query; this.packFilter = pack; this.categoryFilter = TextureCategory.ALL;
        this.armorPartFilter = ArmorPart.ALL; this.toolTypeFilter = ToolType.ALL; refilter();
    }
    /** A category always starts a global search, even when a single pack was open. */
    public void browseCategory(TextureCategory category) {
        this.categoryFilter = Objects.requireNonNull(category); this.armorPartFilter = ArmorPart.ALL; this.toolTypeFilter = ToolType.ALL;
        this.tab = Tab.ALL; this.packFilter = ""; this.query = ""; refilter();
    }
    /** Keep a material/name search while comparing different armor slots across packs. */
    public void browseArmorPart(ArmorPart part) {
        Objects.requireNonNull(part);
        if (categoryFilter != TextureCategory.ARMOR) query = "";
        armorPartFilter = part; toolTypeFilter = ToolType.ALL; categoryFilter = TextureCategory.ARMOR; tab = Tab.ALL; packFilter = ""; refilter();
    }
    /** Tool subcategories keep the current material/name search and include all packs. */
    public void browseToolType(ToolType type) {
        Objects.requireNonNull(type);
        if (categoryFilter != TextureCategory.TOOLS) query = "";
        toolTypeFilter = type; armorPartFilter = ArmorPart.ALL; categoryFilter = TextureCategory.TOOLS; tab = Tab.ALL; packFilter = ""; refilter();
    }
    public void search(String query) {
        this.query = query; refilter();
    }
    private void refilter() {
        if (!ready()) return;
        Collection<TextureRef> pool = switch(tab) {
            case ALL -> scan.textures();
            case FAVORITES -> library.state().favorites().values();
            case SELECTED -> library.state().selected().values();
        };
        filtered = pool.stream().filter(t -> (packFilter.isEmpty() || t.pack().equals(packFilter)) && categoryFilter.matches(t)
                        && (categoryFilter != TextureCategory.ARMOR || armorPartFilter.matches(t))
                        && (categoryFilter != TextureCategory.TOOLS || toolTypeFilter.matches(t)) && t.matches(query))
                .sorted(Comparator.comparing(TextureRef::name).thenComparing(TextureRef::pack)).toList();
        revision++;
    }
    public void focus(TextureRef ref) {
        if (Objects.equals(focused, ref)) return;
        focused = ref; preview = null;previewModel=null;modelNote=""; previewError = ""; revision++;
        long token = ++previewGeneration;
        if (ref == null) return;
        io.execute(() -> {
            try {
                TextureImage image = TextureImage.decode(data(ref));
                PreviewModel model=null;String note="";
                try {model=ModelLoader.load(ref,image,path->{byte[] asset=catalog.readAsset(ref.pack(),path);return asset!=null?asset:fallbackAssets.read(path);});}
                catch(Exception e){note="Modell nicht verfügbar: "+reason(e);}
                PreviewModel loaded=model;String detail=note;
                completed.add(() -> { if (token == previewGeneration) { preview = image;previewModel=loaded;modelNote=detail; revision++; } });
            } catch (Exception e) { completed.add(() -> { if (token == previewGeneration) { previewError = reason(e); revision++; } }); }
        });
    }
    public BufferedImage thumbnail(TextureRef ref) {
        String key = cacheKey(ref);
        if (thumbnails.containsKey(key)) return thumbnails.get(key);
        if (imageErrors.containsKey(key) || !requested.add(key)) return null;
        int generation = scanGeneration;
        io.execute(() -> {
            try {
                BufferedImage frame = TextureImage.decode(data(ref)).frame();
                double scale = 80.0 / Math.max(frame.getWidth(), frame.getHeight());
                int w = Math.max(1, (int)(frame.getWidth() * scale)), h = Math.max(1, (int)(frame.getHeight() * scale));
                BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = thumb.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(frame, 0, 0, w, h, null); g.dispose();
                completed.add(() -> {
                    if (generation != scanGeneration) return;
                    requested.remove(key); thumbnails.put(key, thumb);
                    while (thumbnails.size() > 192) thumbnails.remove(thumbnails.keySet().iterator().next());
                    revision++;
                });
            } catch (Exception e) { completed.add(() -> { if (generation == scanGeneration) { requested.remove(key); imageErrors.put(key, reason(e)); revision++; } }); }
        });
        return null;
    }
    public boolean thumbnailFailed(TextureRef ref) { return imageErrors.containsKey(cacheKey(ref)); }
    private static String cacheKey(TextureRef ref) { return ref.key() + "|" + ref.blob(); }
    private PackCatalog.Data data(TextureRef ref) throws IOException { return ref.blob().isEmpty() ? catalog.read(ref) : library.read(ref); }
    public void refresh() {
        run("Packs werden neu eingelesen…", () -> {
            PackCatalog.Scan next = catalog.scan();
            completed.add(() -> {
                scan = next; scanGeneration++; thumbnails.clear(); requested.clear(); imageErrors.clear();
                TextureRef previous = focused; focused = null; focus(previous);
                if (!packFilter.isEmpty() && scan.packs().stream().noneMatch(p->p.id().equals(packFilter))) packFilter = "";
                message(scanMessage(), !scan.warnings().isEmpty()); refilter();
            });
        }, null);
    }
    public void favorite() {
        TextureRef ref = focused; if (ref == null) return;
        boolean removing = isFavorite(ref);
        run("Favorit wird gespeichert…", () -> library.toggleFavorite(ref, catalog), removing ? "Favorit entfernt." : "Favorit mit lokaler Texturkopie gespeichert.");
    }
    public void select() {
        TextureRef ref = focused; if (ref == null) return;
        boolean removing = isSelected(ref);
        run("Auswahl wird gespeichert…", () -> { if (removing) library.remove(ref.path()); else library.select(ref, catalog); },
                removing ? "Textur aus deinem Pack entfernt." : "Textur im Projekt gespeichert. Zum Anwenden Pack exportieren.");
    }
    public void rename(String name) {
        if (name.strip().isEmpty()) { message("Bitte einen Pack-Namen eingeben.", true); return; }
        run("Name wird gespeichert…", () -> library.rename(name), "Pack-Name gespeichert.");
    }
    public void export(boolean apply) {
        export(apply, name());
    }
    public void export(boolean apply, String name) {
        if (!ready() || busy) return;
        if (name.strip().isEmpty()) { message("Bitte einen Pack-Namen eingeben.", true); return; }
        busy = true; message("Pack wird exportiert…", false);
        io.execute(() -> {
            try {
                if (!name.strip().equals(library.state().name())) library.rename(name);
                Path result = PackExporter.export(library, catalog.root(), packMetadata);
                completed.add(() -> {
                    if (apply) {
                        message("Pack wird in Minecraft aktiviert…", false);
                        try { activate.accept(result); } catch (Exception e) { activationFinished(reason(e)); }
                    } else { busy = false; message("Pack-ZIP gespeichert. Unter Ressourcenpakete auswählbar.", false); }
                });
            } catch (Exception e) { completed.add(() -> { busy = false; message(reason(e), true); }); }
        });
    }
    public void activationFinished(String failure) {
        completed.add(() -> { busy = false; message(failure == null ? "Dein Pack ist aktiv und für den nächsten Start gespeichert." : "Export gespeichert; Aktivierung fehlgeschlagen: " + failure, failure != null); });
    }
    private interface Action { void run() throws Exception; }
    private void run(String progress, Action action, String success) {
        if (!ready() || busy) return;
        busy = true; message(progress, false);
        io.execute(() -> {
            try {
                action.run();
                // Completion is inserted before activation callbacks are handled by the next UI poll.
                completed.add(() -> { busy = false; if (success != null) message(success, false); refilter(); });
            } catch (Exception e) { completed.add(() -> { busy = false; message(reason(e), true); }); }
        });
    }
    private String scanMessage() { return scan.packs().size() + " Packs · " + scan.textures().size() + " Texturen" + (scan.warnings().isEmpty() ? " · alles lokal" : " · Hinweis: " + scan.warnings().getFirst()); }
    private void message(String text, boolean error) { this.status = text; this.error = error; revision++; }
    private static String reason(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    @Override public void close() {
        io.shutdown();
        try { io.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
