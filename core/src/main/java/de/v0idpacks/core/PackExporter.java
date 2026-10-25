package de.v0idpacks.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

public final class PackExporter {
    private static final Pattern PANORAMA_FACE = Pattern.compile("(assets/[a-z0-9_.-]+/textures/gui/title/background/panorama)_([0-5])\\.png");
    private PackExporter() {}
    /** resourceFormat is read from Minecraft's own version.json, never guessed. */
    public static Path export(LibraryStore library, Path packs, JsonObject packMetadata) throws IOException {
        LibraryStore.State state = library.state();
        if (state.selected().isEmpty()) throw new IOException("Wähle zuerst mindestens eine Textur aus");
        validatePanoramas(library, state);
        byte[] logo = library.readLogo(state);
        Files.createDirectories(packs);
        // Content-addressed names keep the previously active ZIP readable on Windows during reload.
        JsonObject identity = new JsonObject();
        identity.addProperty("project_id", library.projectId());
        identity.addProperty("name", state.name());
        identity.addProperty("logo", state.logo());
        identity.add("selected", new Gson().toJsonTree(state.selected()));
        byte[] signature = new Gson().toJson(identity).getBytes(StandardCharsets.UTF_8);
        String hash = SafeFiles.hash(signature, packMetadata.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        Path target = packs.resolve(PackPresentation.fileName(state.name(), hash));
        Path temp = Files.createTempFile(packs, ".v0idpacks-export-", ".tmp");
        try {
            JsonObject pack = packMetadata.deepCopy();
            pack.addProperty("description", state.name() + " · " + state.selected().size() + " Texturen · v0idpacks");
            JsonObject metadata = new JsonObject(); metadata.add("pack", pack);
            JsonObject presentation = new JsonObject(); presentation.addProperty("name", state.name());
            presentation.addProperty("project_id", library.projectId());
            metadata.add("v0idpacks", presentation);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                write(zip, "pack.mcmeta", new Gson().toJson(metadata).getBytes(StandardCharsets.UTF_8));
                if (logo.length > 0) write(zip, "pack.png", logo);
                for (TextureRef texture : state.selected().values()) {
                    PackCatalog.Data data = library.read(texture);
                    write(zip, texture.path(), data.png());
                    if (data.metadata().length > 0) write(zip, texture.path() + ".mcmeta", data.metadata());
                }
            }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.exists(target)) {
                if (Files.mismatch(temp, target) != -1) throw new IOException("Vorhandener Export wurde extern verändert: " + target.getFileName());
            } else SafeFiles.replace(temp, target);
            return target;
        } finally { Files.deleteIfExists(temp); }
    }
    private static void validatePanoramas(LibraryStore library, LibraryStore.State state) throws IOException {
        Map<String, TextureRef[]> panoramas = new TreeMap<>();
        for (TextureRef texture : state.selected().values()) {
            var match = PANORAMA_FACE.matcher(texture.path());
            if (match.matches()) panoramas.computeIfAbsent(match.group(1), key -> new TextureRef[6])
                    [Integer.parseInt(match.group(2))] = texture;
        }
        for (TextureRef[] faces : panoramas.values()) {
            // Missing faces would fall back to other active packs, whose dimensions are unknown at export time.
            for (int i = 0; i < faces.length; i++) {
                if (faces[i] == null) throw new IOException("Panorama unvollständig: panorama_" + i
                        + ".png fehlt. Alle 6 Seiten wählen oder Panorama entfernen.");
            }
            int size = 0;
            for (TextureRef face : faces) {
                TextureImage image = TextureImage.decode(library.read(face));
                if (image.width() != image.height()) throw new IOException("Panorama: " + face.name()
                        + ".png ist " + image.width() + "x" + image.height() + ". Alle 6 Seiten müssen quadratisch und gleich groß sein.");
                if (size != 0 && image.width() != size) throw new IOException("Panorama: " + face.name()
                        + ".png ist " + image.width() + "x" + image.height() + " statt " + size + "x" + size
                        + ". Alle 6 Seiten müssen gleich groß sein.");
                size = image.width();
            }
        }
    }
    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0);
        zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }
}
