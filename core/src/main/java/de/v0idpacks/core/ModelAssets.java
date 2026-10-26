package de.v0idpacks.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source-pack resources required by an item, including all conditional model branches. */
final class ModelAssets {
    private final PackCatalog catalog;
    private final String pack;
    private final Map<String, byte[]> files = new TreeMap<>();
    private final Set<String> visited = new HashSet<>();
    private long bytes;

    private ModelAssets(PackCatalog catalog, String pack) { this.catalog = catalog; this.pack = pack; }

    static Map<String, byte[]> collect(TextureRef ref, PackCatalog catalog) throws IOException {
        ModelAssets result = new ModelAssets(catalog, ref.pack());
        for (String root : roots(ref)) result.visit(root, 0);
        if (CrystalTextures.contains(ref.path())) result.visit(CrystalTextures.companion(ref.path()), 0);
        // The chosen PNG and animation metadata are stored by LibraryStore itself.
        result.files.remove(ref.path());
        result.files.remove(ref.path() + ".mcmeta");
        return result.files;
    }

    private static Set<String> roots(TextureRef ref) {
        Set<String> roots = new TreeSet<>();
        String[] parts = ref.path().split("/", 4);
        String relative = parts[3].substring(0, parts[3].length() - 4);
        if (ref.category().equals("item") || ref.category().equals("block")) {
            roots.add("assets/" + parts[1] + "/models/" + relative + ".json");
            if (ref.category().equals("item")) roots.add("assets/" + parts[1] + "/items/" + relative.substring(5) + ".json");
        }
        if (CrystalTextures.contains(ref.path())) {
            roots.add("assets/minecraft/items/end_crystal.json");
            roots.add("assets/minecraft/models/item/end_crystal.json");
        }
        // Shield textures are entity atlases; their item models have different filenames.
        if (relative.matches("entity/shield(?:/.*|_.*)?") || relative.equals("item/shield")) {
            roots.add("assets/" + parts[1] + "/items/shield.json");
            roots.add("assets/" + parts[1] + "/models/item/shield.json");
            roots.add("assets/" + parts[1] + "/models/item/shield_blocking.json");
        }
        return roots;
    }

    /** Give each saved model graph private IDs so shared parents cannot resize other selected items. */
    static Map<String, byte[]> export(TextureRef ref, LibraryStore library) throws IOException {
        Map<String, String> models = new TreeMap<>();
        ref.assets().forEach((path, hash) -> { if (path.contains("/models/")) models.put(path, hash); });
        String prefix = "v0idpacks:source/" + SafeFiles.hash(new Gson().toJson(models).getBytes(StandardCharsets.UTF_8), new byte[0]) + "/";
        Map<String, String> ids = new HashMap<>();
        for (String path : models.keySet()) {
            String[] parts = path.split("/", 4);
            String relative = parts[3].substring(0, parts[3].length() - 5);
            ids.put(parts[1] + ":" + relative, prefix + parts[1] + "/" + relative);
        }
        Set<String> roots = roots(ref);
        Map<String, byte[]> result = new TreeMap<>();
        for (String path : ref.assets().keySet()) {
            byte[] data = library.readAsset(ref, path);
            if (path.endsWith(".json")) {
                JsonElement json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
                String before = json.toString();
                rewrite(json, ids, 0);
                if (!before.equals(json.toString())) data = json.toString().getBytes(StandardCharsets.UTF_8);
            }
            if (models.containsKey(path)) {
                String[] parts = path.split("/", 4);
                String id = ids.get(parts[1] + ":" + parts[3].substring(0, parts[3].length() - 5));
                result.put("assets/v0idpacks/models/" + id.substring("v0idpacks:".length()) + ".json", data);
                if (roots.contains(path)) result.put(path, data);
            } else result.put(path, data);
        }
        return result;
    }

    private static void rewrite(JsonElement value, Map<String, String> ids, int depth) throws IOException {
        if (depth > 64) throw new IOException("Modell ist zu tief verschachtelt");
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) rewrite(child, ids, depth + 1);
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if (Set.of("parent", "model", "base").contains(entry.getKey())
                        && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String id = child.getAsString();
                    String replacement = ids.get(id.contains(":") ? id : "minecraft:" + id);
                    if (replacement != null) entry.setValue(new JsonPrimitive(replacement));
                } else if (child.isJsonObject() || child.isJsonArray()) rewrite(child, ids, depth + 1);
            }
        }
    }

    private void visit(String path, int depth) throws IOException {
        if (!PackCatalog.safeAsset(path)) throw new IOException("Ungültiger Modellpfad: " + path);
        if (visited.contains(path)) return;
        if (depth > 64 || visited.size() >= 512) throw new IOException("Zu viele Modell-Abhängigkeiten");
        visited.add(path);
        byte[] data = catalog.readAsset(pack, path);
        if (data == null) return; // Vanilla resources remain supplied by Minecraft.
        bytes += data.length;
        if (bytes > 64L * 1024 * 1024) throw new IOException("Modellressourcen überschreiten das Größenlimit");
        files.put(path, data);
        if (path.endsWith(".png")) {
            byte[] metadata = catalog.readAsset(pack, path + ".mcmeta");
            TextureImage.decode(new PackCatalog.Data(data, metadata == null ? new byte[0] : metadata));
            visit(path + ".mcmeta", depth + 1);
        } else if (path.endsWith(".json")) {
            try {
                JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
                if (path.contains("/models/")) {
                    if (json.has("parent")) reference(json.get("parent").getAsString(), "models", ".json", depth);
                    if (json.has("textures")) {
                        for (var texture : json.getAsJsonObject("textures").entrySet()) {
                            String id = texture.getValue().getAsString();
                            if (!id.startsWith("#")) reference(id, "textures", ".png", depth);
                        }
                    }
                    if (json.has("overrides")) walk(json.get("overrides"), depth);
                } else walk(json, depth);
            } catch (RuntimeException e) { throw new IOException("Ungültiges Modell: " + path, e); }
        }
    }

    private void walk(JsonElement value, int depth) throws IOException {
        if (depth > 64) throw new IOException("Item-Modell ist zu tief verschachtelt");
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) walk(child, depth + 1);
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if ((entry.getKey().equals("model") || entry.getKey().equals("base"))
                        && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString())
                    reference(child.getAsString(), "models", ".json", depth);
                else if (child.isJsonObject() || child.isJsonArray()) walk(child, depth + 1);
            }
        }
    }

    private void reference(String id, String folder, String suffix, int depth) throws IOException {
        String[] parts = id.contains(":") ? id.split(":", 2) : new String[]{"minecraft", id};
        visit("assets/" + parts[0] + "/" + folder + "/" + parts[1] + suffix, depth + 1);
    }
}
