package hr.kricco.contractor.controller;

import hr.kricco.contractor.exception.NotFoundException;
import hr.kricco.contractor.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static hr.kricco.contractor.TestUploads.PNG;
import static hr.kricco.contractor.TestUploads.deleteFilesIn;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/files/{filename} is public, so the requests carry no user. No database rows are needed.
@SpringBootTest
@AutoConfigureMockMvc
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileStorageService fileStorageService;

    @Value("${app.upload-dir}")
    private Path uploadDir;

    @AfterEach
    void deleteStoredFiles() throws Exception {
        deleteFilesIn(uploadDir);
        Files.deleteIfExists(outsideFile());
    }

    @Test
    void servesStoredPhotoWithoutLoginAsDownload() throws Exception {
        String filename = fileStorageService.store(PNG, "png");

        mockMvc.perform(get("/api/files/{filename}", filename))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void unknownFileReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/files/{filename}", "00000000-0000-0000-0000-000000000000.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("File not found"));
    }

    // Only image files are served, even if something else ends up in the folder
    @Test
    void fileWithoutImageExtensionReturnsNotFound() throws Exception {
        Files.createDirectories(uploadDir);
        Files.writeString(uploadDir.resolve("notes.txt"), "not a photo");

        mockMvc.perform(get("/api/files/{filename}", "notes.txt"))
                .andExpect(status().isNotFound());
    }

    // A URL with "../" is refused by Spring Security's firewall already, so the service check is tested directly
    @Test
    void fileOutsideUploadFolderIsNotFound() throws Exception {
        Files.write(outsideFile(), PNG);

        assertThatThrownBy(() -> fileStorageService.loadPhoto("../" + outsideFile().getFileName()))
                .isInstanceOf(NotFoundException.class);
    }

    private Path outsideFile() {
        return uploadDir.toAbsolutePath().getParent().resolve("outside-upload-dir.png");
    }
}
