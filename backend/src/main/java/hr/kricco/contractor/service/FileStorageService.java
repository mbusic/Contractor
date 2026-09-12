package hr.kricco.contractor.service;

import hr.kricco.contractor.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

// Photo files in app.upload-dir (./uploads). The database only keeps the file name.
// File errors are unexpected (disk full, no rights), so they become UncheckedIOException and a 500.
@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    // Saves the bytes as <uuid>.<extension> and returns that name
    public String store(byte[] content, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(filename), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't store " + filename, e);
        }
        return filename;
    }

    // Only a file directly in the upload folder with an image extension. Anything else, "../" included, is 404.
    public StoredPhoto loadPhoto(String filename) {
        Path file = uploadDir.resolve(filename).normalize();
        Optional<ImageType> type = ImageType.fromFileName(filename);
        boolean insideUploadDir = uploadDir.equals(file.getParent());
        if (!insideUploadDir || type.isEmpty() || !Files.isRegularFile(file)) {
            throw new NotFoundException("File not found");
        }
        return new StoredPhoto(file, type.get());
    }

    // No error if the file is already gone
    public void delete(String filename) {
        try {
            Files.deleteIfExists(uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Can't delete " + filename, e);
        }
    }
}
