package hr.kricco.contractor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

// Test data for photo uploads. Only the first bytes count for the type check, so a few bytes are enough.
public final class TestUploads {

    public static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F');
    public static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    public static final byte[] GIF = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');

    private TestUploads() {
    }

    // Files written by a test stay on disk even when its transaction rolls back
    public static void deleteFilesIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Path> filesIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
