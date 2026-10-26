package de.v0idpacks.core;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

/** A source texture, or an immutable local snapshot when blob is nonempty. */
public record TextureRef(String pack, String path, String blob, Map<String, String> assets) {
    public TextureRef(String pack, String path, String blob) { this(pack, path, blob, Map.of()); }
    public TextureRef {
        if (pack == null || pack.isBlank() || pack.contains("/") || pack.contains("\\") || pack.equals(".."))
            throw new IllegalArgumentException("Ungültiger Pack-Name");
        if (!safePath(path)) throw new IllegalArgumentException("Ungültiger Ressourcenpfad: " + path);
        if (blob == null) blob = "";
        if (!blob.isEmpty() && !blob.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Ungültige Kopie");
        assets = assets == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(assets));
        if (assets.size() > 512) throw new IllegalArgumentException("Zu viele Modellressourcen");
        assets.forEach((asset, hash) -> {
            if (!PackCatalog.safeAsset(asset) || !asset.startsWith("assets/") || hash == null || !hash.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Ungültige Modellressource");
        });
    }
    public String key() { return pack + "|" + path; }
    public String name() { return path.substring(path.lastIndexOf('/') + 1, path.length() - 4); }
    public String category() {
        String p = path.substring(path.indexOf("/textures/") + 10);
        return p.contains("/") ? p.substring(0, p.indexOf('/')) : "other";
    }
    public boolean matches(String query) {
        String text = MinecraftFormatting.plain(pack + " " + path).toLowerCase(Locale.ROOT);
        for (String word : MinecraftFormatting.plain(query).strip().toLowerCase(Locale.ROOT).split("\\s+"))
            if (!text.contains(word)) return false;
        return true;
    }
    public static boolean safePath(String path) {
        return path != null && path.length() <= 512
                && path.matches("assets/[a-z0-9_.-]+/textures/[a-z0-9_./-]+\\.png")
                && !path.contains("..") && !path.contains("//");
    }
}
