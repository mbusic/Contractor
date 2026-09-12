package hr.kricco.contractor.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

// The photo types we accept. The file's first bytes (magic bytes) decide the type.
// The uploaded name and Content-Type are only a quick first check, because the client can fake both.
public enum ImageType {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String mediaType;

    ImageType(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    // The extension a stored file gets
    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    // JPEG: FF D8 FF. PNG: 89 "PNG". GIF: "GIF8". WebP: "RIFF", 4 size bytes, "WEBP".
    public static Optional<ImageType> detect(byte[] content) {
        if (hasBytes(content, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (hasBytes(content, 0, 0x89, 'P', 'N', 'G')) {
            return Optional.of(PNG);
        }
        if (hasBytes(content, 0, 'G', 'I', 'F', '8')) {
            return Optional.of(GIF);
        }
        if (hasBytes(content, 0, 'R', 'I', 'F', 'F') && hasBytes(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    // By the file name's extension. "jpeg" counts as JPEG too.
    public static Optional<ImageType> fromFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return Optional.empty();
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (extension.equals("jpeg")) {
            return Optional.of(JPEG);
        }
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(extension))
                .findFirst();
    }

    public static boolean isAllowedMediaType(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(type -> type.mediaType.equalsIgnoreCase(mediaType));
    }

    private static boolean hasBytes(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
