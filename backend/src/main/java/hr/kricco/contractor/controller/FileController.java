package hr.kricco.contractor.controller;

import hr.kricco.contractor.service.FileStorageService;
import hr.kricco.contractor.service.StoredPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Serves the photo files. Public (see SecurityConfig), so no @PreAuthorize.
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // Content-Type comes from the stored type, which the upload detected from the file's bytes.
    // "attachment" makes a browser download the file instead of showing it when the URL is opened directly.
    // <img> tags ignore it, so the UI and the documents still show the photos.
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        StoredPhoto photo = fileStorageService.loadPhoto(filename);
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.type().mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(photo.path()));
    }
}
