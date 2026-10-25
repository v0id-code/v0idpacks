package de.v0idpacks.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

/** Never extracts source archives. Symlinks, traversal and oversized entries are rejected. */
public final class PackCatalog {
    public static final String OUTPUT_PREFIX = "v0idpacks-custom-";
    public static final int MAX_ENTRIES = 100_000;
    private final Path root;
    public record Scan(List<TextureRef> textures, List<PackInfo> packs, List<String> warnings) {}
    public record Data(byte[] png, byte[] metadata) {}
    public PackCatalog(Path root) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
    }
    public Path root() { return root; }
    public static boolean isGeneratedPack(String name) {
        return name.matches("v0idpacks-custom-[a-f0-9]{32}\\.zip")
                || name.matches("[^/\\\\]+ \\[v0idpacks-[a-f0-9]{32}\\]\\.zip");
    }

    public Scan scan() throws IOException {
        List<TextureRef> textures = new ArrayList<>();
        List<String> packs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (var children = Files.list(root)) {
            for (Path pack : children.sorted().toList()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                String name = pack.getFileName().toString();
                if (Files.isSymbolicLink(pack) || isGeneratedPack(name) || name.startsWith(".")) continue;
                try {
                    if (Files.isDirectory(pack, LinkOption.NOFOLLOW_LINKS)) {
                        if (!Files.isRegularFile(pack.resolve("pack.mcmeta"), LinkOption.NOFOLLOW_LINKS)) continue;
                        packs.add(name);
                        Path assets = pack.resolve("assets");
                        if (!Files.isDirectory(assets) || Files.isSymbolicLink(assets)) continue;
                        try (var files = Files.walk(assets)) {
                            var iterator = files.iterator();
                            int visited = 0;
                            while (iterator.hasNext()) {
                                Path file = iterator.next();
                                if (++visited > MAX_ENTRIES * 2) throw new IOException("Zu viele Dateien");
                                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                                String entry = pack.relativize(file).toString().replace('\\', '/');
                                if (TextureRef.safePath(entry) && Files.size(file) <= SafeFiles.MAX_TEXTURE)
                                    add(textures, new TextureRef(name, entry, ""));
                            }
                        }
                    } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        try (ZipFile zip = new ZipFile(pack.toFile())) {
                            if (zip.getEntry("pack.mcmeta") == null) {
                                warnings.add(name + ": pack.mcmeta fehlt im ZIP-Hauptverzeichnis");
                                continue;
                            }
                            if (zip.size() > MAX_ENTRIES * 2) throw new IOException("Zu viele ZIP-Einträge");
                            packs.add(name);
                            var entries = zip.entries();
                            Set<String> seen = new HashSet<>();
                            while (entries.hasMoreElements()) {
                                ZipEntry entry = entries.nextElement();
                                if (!entry.isDirectory() && TextureRef.safePath(entry.getName())
                                        && entry.getSize() <= SafeFiles.MAX_TEXTURE && seen.add(entry.getName()))
                                    add(textures, new TextureRef(name, entry.getName(), ""));
                            }
                        }
                    }
                } catch (IOException | IllegalArgumentException e) { warnings.add(name + ": " + e.getMessage()); }
            }
        }
        textures.sort(Comparator.comparing(TextureRef::name).thenComparing(TextureRef::pack));
        Map<String,Integer> counts=new HashMap<>();for(TextureRef ref:textures)counts.merge(ref.pack(),1,Integer::sum);
        List<PackInfo> info=new ArrayList<>();
        for(String name:packs) {
            String description="";BufferedImage icon=null;
            try {
                byte[] meta=readAsset(name,"pack.mcmeta");
                if(meta!=null) {JsonObject root=JsonParser.parseString(new String(meta,StandardCharsets.UTF_8)).getAsJsonObject();if(root.has("pack"))description=MinecraftFormatting.component(root.getAsJsonObject("pack").get("description")).replace('\n',' ');}
            } catch(IOException|RuntimeException e){warnings.add(name+": pack.mcmeta konnte nicht gelesen werden");}
            try {
                byte[] png=readAsset(name,"pack.png");
                if(png!=null) {BufferedImage source=TextureImage.decode(new Data(png,new byte[0])).frame();icon=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=icon.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(source,0,0,64,64,null);g.dispose();}
            } catch(IOException|RuntimeException e){warnings.add(name+": Pack-Icon konnte nicht gelesen werden");}
            String display=name.toLowerCase(Locale.ROOT).endsWith(".zip")?name.substring(0,name.length()-4):name;
            info.add(new PackInfo(name,display,description,counts.getOrDefault(name,0),icon));
        }
        return new Scan(List.copyOf(textures), List.copyOf(info), List.copyOf(warnings));
    }
    private static void add(List<TextureRef> entries, TextureRef entry) throws IOException {
        if (entries.size() >= MAX_ENTRIES) throw new IOException("Maximal 100.000 Texturen pro Bibliothek");
        entries.add(entry);
    }
    public Data read(TextureRef ref) throws IOException {
        Path pack = root.resolve(ref.pack()).normalize();
        if (!pack.startsWith(root) || Files.isSymbolicLink(pack)) throw new IOException("Unsicherer Pack-Pfad");
        if (Files.isDirectory(pack)) {
            Path png = safeChild(pack, ref.path());
            Path meta = png.resolveSibling(png.getFileName() + ".mcmeta");
            byte[] metadata = Files.exists(meta) ? SafeFiles.read(safeChild(pack, ref.path() + ".mcmeta"), SafeFiles.MAX_METADATA) : new byte[0];
            return new Data(SafeFiles.read(png, SafeFiles.MAX_TEXTURE), metadata);
        }
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            return new Data(readEntry(zip, ref.path(), SafeFiles.MAX_TEXTURE, true),
                    readEntry(zip, ref.path() + ".mcmeta", SafeFiles.MAX_METADATA, false));
        }
    }
    /** Optional metadata/model dependency; absence is distinct from malformed or unsafe input. */
    public byte[] readAsset(String name,String asset) throws IOException {
        if(name==null||name.isBlank()||name.contains("/")||name.contains("\\")||name.equals(".."))throw new IOException("Ungültiger Pack-Pfad");
        if(!safeAsset(asset))throw new IOException("Unsicherer Ressourcenpfad");
        Path pack=root.resolve(name);if(Files.isSymbolicLink(pack))throw new IOException("Verknüpfungen werden nicht gelesen");
        int max=asset.endsWith(".png")?SafeFiles.MAX_TEXTURE:SafeFiles.MAX_METADATA;
        if(Files.isDirectory(pack)) {Path path=pack.resolve(asset);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return null;return SafeFiles.read(safeChild(pack,asset),max);}
        if(!Files.exists(pack))return null;
        try(ZipFile zip=new ZipFile(pack.toFile())) {if(zip.getEntry(asset)==null)return null;return readEntry(zip,asset,max,true);}
    }
    public static boolean safeAsset(String path) {
        return path!=null && path.length()<=512 && !path.contains("..") && !path.contains("//") && (path.equals("pack.png")||path.equals("pack.mcmeta")||path.matches("assets/[a-z0-9_.-]+/(?:textures/[a-z0-9_./-]+\\.png(?:\\.mcmeta)?|models/[a-z0-9_./-]+\\.json|items/[a-z0-9_./-]+\\.json)"));
    }
    private static Path safeChild(Path pack, String name) throws IOException {
        Path path = pack.resolve(name).normalize();
        if (!path.startsWith(pack)) throw new IOException("Unsicherer Ressourcenpfad");
        Path current = path;
        while (!current.equals(pack)) {
            if (Files.isSymbolicLink(current)) throw new IOException("Verknüpfungen werden nicht gelesen");
            current = current.getParent();
        }
        if (!path.toRealPath().startsWith(pack.toRealPath())) throw new IOException("Ressource außerhalb des Packs");
        return path;
    }
    private static byte[] readEntry(ZipFile zip, String path, int max, boolean required) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            if (required) throw new IOException("Textur nicht mehr vorhanden: " + path);
            return new byte[0];
        }
        if (entry.getSize() > max) throw new IOException("Ressource zu groß: " + path);
        try (InputStream in = zip.getInputStream(entry)) { return SafeFiles.read(in, max); }
    }
}
