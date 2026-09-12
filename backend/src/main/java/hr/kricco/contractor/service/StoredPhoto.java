package hr.kricco.contractor.service;

import java.nio.file.Path;

// A photo file found in app.upload-dir, with its type taken from the stored extension
public record StoredPhoto(
        Path path,
        ImageType type
) {
}
