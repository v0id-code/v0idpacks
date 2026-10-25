package de.v0idpacks.core;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Presentation is independent of the immutable filename used for Windows resource reloads. */
public final class PackPresentation {
    private PackPresentation() {}

    public static String fileName(String name, String hash) {
        String safe = name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").strip();
        if (safe.isEmpty()) safe = "Mein v0idpack";
        return safe + " [v0idpacks-" + hash + "].zip";
    }

    public static String readName(Path packs, String id) throws IOException {
        String name = readProperty(packs, id, "name");
        return name == null || name.isBlank() || name.length() > 64 ? null : name;
    }
    public static String readProjectId(Path packs, String id) throws IOException {
        String project = readProperty(packs, id, "project_id");
        return project != null && project.matches("[a-f0-9]{32}") ? project : null;
    }
    public static boolean belongsToProject(Path packs, String id, String project) {
        if (project == null || !id.startsWith("file/") || !PackCatalog.isGeneratedPack(id.substring(5))) return false;
        try {
            String previous = readProjectId(packs, id);
            return previous == null ? project.equals(LibraryStore.LEGACY_PROJECT_ID) : previous.equals(project);
        } catch (IOException e) { return false; }
    }
    private static String readProperty(Path packs, String id, String property) throws IOException {
        if (!id.startsWith("file/") || !PackCatalog.isGeneratedPack(id.substring(5))) return null;
        try (ZipFile zip = new ZipFile(packs.resolve(id.substring(5)).toFile())) {
            var entry = zip.getEntry("pack.mcmeta");
            if (entry == null) return null;
            try (var input = zip.getInputStream(entry)) {
                var metadata = JsonParser.parseString(new String(SafeFiles.read(input, SafeFiles.MAX_METADATA), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!metadata.has("v0idpacks")) return null;
                var presentation = metadata.getAsJsonObject("v0idpacks");
                return presentation.has(property) ? presentation.get(property).getAsString() : null;
            }
        } catch (RuntimeException e) { throw new IOException("Pack-Name konnte nicht gelesen werden", e); }
    }
}
