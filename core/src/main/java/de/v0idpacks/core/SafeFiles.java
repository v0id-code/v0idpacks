package de.v0idpacks.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class SafeFiles {
    static final int MAX_TEXTURE = 32 * 1024 * 1024;
    static final int MAX_METADATA = 1024 * 1024;
    private SafeFiles() {}

    static byte[] read(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Datei überschreitet das Größenlimit");
        return bytes;
    }
    static byte[] read(Path path, int limit) throws IOException {
        try (InputStream in = Files.newInputStream(path)) { return read(in, limit); }
    }
    static String hash(byte[] png, byte[] metadata) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(png.length).array());
            digest.update(png);
            digest.update(metadata);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    static void atomicWrite(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".v0idpacks-", ".tmp");
        try {
            Files.write(temp, data);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            replace(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }
    static void replace(Path temp, Path target) throws IOException {
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
