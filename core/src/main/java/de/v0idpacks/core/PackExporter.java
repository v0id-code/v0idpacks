package de.v0idpacks.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.zip.*;

public final class PackExporter {
    private PackExporter() {}
    /** resourceFormat is read from Minecraft's own version.json, never guessed. */
    public static Path export(LibraryStore library, Path packs, JsonObject packMetadata) throws IOException {
        LibraryStore.State state = library.state();
        if (state.selected().isEmpty()) throw new IOException("Wähle zuerst mindestens eine Textur aus");
        Files.createDirectories(packs);
        // Content-addressed names keep the previously active ZIP readable on Windows during reload.
        JsonObject identity = new JsonObject();
        identity.addProperty("name", state.name());
        identity.add("selected", new Gson().toJsonTree(state.selected()));
        byte[] signature = new Gson().toJson(identity).getBytes(StandardCharsets.UTF_8);
        String hash = SafeFiles.hash(signature, packMetadata.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        Path target = packs.resolve(PackCatalog.OUTPUT_PREFIX + hash + ".zip");
        Path temp = Files.createTempFile(packs, ".v0idpacks-export-", ".tmp");
        try {
            JsonObject pack = packMetadata.deepCopy();
            pack.addProperty("description", state.name() + " · " + state.selected().size() + " Texturen · v0idpacks");
            JsonObject metadata = new JsonObject(); metadata.add("pack", pack);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                write(zip, "pack.mcmeta", new Gson().toJson(metadata).getBytes(StandardCharsets.UTF_8));
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
    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0);
        zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }
}
