package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import hr.kricco.contractor.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.jayway.jsonpath.JsonPath.read;
import static hr.kricco.contractor.TestUploads.GIF;
import static hr.kricco.contractor.TestUploads.JPEG;
import static hr.kricco.contractor.TestUploads.PNG;
import static hr.kricco.contractor.TestUploads.WEBP;
import static hr.kricco.contractor.TestUploads.deleteFilesIn;
import static hr.kricco.contractor.TestUploads.filesIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Photo upload and delete on /api/orders/{id}/photos. Runs against contractor_test, each test is rolled back.
// The files land in app.upload-dir (build/test-uploads) and are removed after each test.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderPhotoControllerTest {

    private static final String URL_PATTERN = "/api/files/[0-9a-f-]{36}\\.%s";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.upload-dir}")
    private Path uploadDir;

    private User office;
    private User servicer;
    private User otherServicer;
    private Order order;

    @BeforeEach
    void setUp() {
        office = saveUser("office", Role.OFFICE);
        servicer = saveUser("servicer", Role.SERVICER);
        otherServicer = saveUser("servicer2", Role.SERVICER);
        order = saveOrder(servicer);
    }

    @AfterEach
    void deleteUploadedFiles() {
        deleteFilesIn(uploadDir);
    }

    // Upload

    @Test
    void uploadStoresFileAndReturnsPhoto() throws Exception {
        String body = upload(order, "photo.jpg", "image/jpeg", JPEG, servicer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].url").value(matchesPattern(URL_PATTERN.formatted("jpg"))))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();

        Path stored = uploadDir.resolve(fileNameOf(read(body, "$.photos[0].url")));
        assertThat(Files.readAllBytes(stored)).isEqualTo(JPEG);
    }

    @ParameterizedTest
    @CsvSource({
            "photo.jpeg, image/jpeg, JPEG, jpg",
            "photo.png,  image/png,  PNG,  png",
            "photo.gif,  image/gif,  GIF,  gif",
            "photo.webp, image/webp, WEBP, webp"
    })
    void everyAllowedTypeCanBeUploaded(String name, String contentType, String type, String storedExtension)
            throws Exception {
        upload(order, name, contentType, bytesOf(type), office)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos[0].url").value(matchesPattern(URL_PATTERN.formatted(storedExtension))));
    }

    @Test
    void storedTypeComesFromTheBytesNotTheName() throws Exception {
        upload(order, "photo.jpg", "image/jpeg", PNG, servicer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos[0].url").value(matchesPattern(URL_PATTERN.formatted("png"))));
    }

    @Test
    void fileThatOnlyLooksLikeAnImageReturnsBadRequest() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        upload(order, "photo.jpg", "image/jpeg", html, servicer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is not a valid JPEG, PNG, GIF or WebP image"));

        assertThat(filesIn(uploadDir)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "notes.txt, image/jpeg",
            "photo.jpg, text/plain",
            "photo,     image/jpeg"
    })
    void wrongNameOrContentTypeReturnsBadRequest(String name, String contentType) throws Exception {
        upload(order, name, contentType, JPEG, servicer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only JPEG, PNG, GIF or WebP images are allowed"));
    }

    @Test
    void seventhPhotoReturnsBadRequest() throws Exception {
        for (int i = 0; i < 6; i++) {
            upload(order, "photo.jpg", "image/jpeg", JPEG, servicer).andExpect(status().isCreated());
        }

        upload(order, "photo.jpg", "image/jpeg", JPEG, servicer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("An order can have at most 6 photos"));

        assertThat(filesIn(uploadDir)).hasSize(6);
    }

    @Test
    void servicerCannotUploadToAnotherServicersOrder() throws Exception {
        upload(order, "photo.jpg", "image/jpeg", JPEG, otherServicer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't change this order"));
    }

    @Test
    void uploadWithoutFilePartReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/orders/{id}/photos", order.getId()).with(as(servicer)))
                .andExpect(status().isBadRequest());
    }

    // Delete

    @Test
    void deletePhotoRemovesRowAndFile() throws Exception {
        String body = upload(order, "photo.jpg", "image/jpeg", JPEG, servicer)
                .andReturn().getResponse().getContentAsString();
        Integer photoId = read(body, "$.photos[0].id");

        mockMvc.perform(delete("/api/orders/{id}/photos/{photoId}", order.getId(), photoId).with(as(servicer)))
                .andExpect(status().isNoContent());

        assertThat(order.getPhotos()).isEmpty();
        assertThat(filesIn(uploadDir)).isEmpty();
    }

    @Test
    void deletePhotoOfAnotherOrderReturnsNotFound() throws Exception {
        String body = upload(order, "photo.jpg", "image/jpeg", JPEG, servicer)
                .andReturn().getResponse().getContentAsString();
        Integer photoId = read(body, "$.photos[0].id");
        Order otherOrder = saveOrder(servicer);

        mockMvc.perform(delete("/api/orders/{id}/photos/{photoId}", otherOrder.getId(), photoId).with(as(servicer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Photo not found"));

        assertThat(filesIn(uploadDir)).hasSize(1);
    }

    @Test
    void servicerCannotDeletePhotoOfAnotherServicersOrder() throws Exception {
        String body = upload(order, "photo.jpg", "image/jpeg", JPEG, servicer)
                .andReturn().getResponse().getContentAsString();
        Integer photoId = read(body, "$.photos[0].id");

        mockMvc.perform(delete("/api/orders/{id}/photos/{photoId}", order.getId(), photoId).with(as(otherServicer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOrderDeletesItsPhotoFiles() throws Exception {
        upload(order, "photo.jpg", "image/jpeg", JPEG, servicer).andExpect(status().isCreated());
        upload(order, "photo.png", "image/png", PNG, servicer).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/orders/{id}", order.getId()).with(as(office)))
                .andExpect(status().isNoContent());

        assertThat(filesIn(uploadDir)).isEmpty();
    }

    private ResultActions upload(Order target, String name, String contentType, byte[] content, User user)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, contentType, content);
        return mockMvc.perform(multipart("/api/orders/{id}/photos", target.getId()).file(file).with(as(user)));
    }

    private static byte[] bytesOf(String type) {
        return switch (type) {
            case "JPEG" -> JPEG;
            case "PNG" -> PNG;
            case "GIF" -> GIF;
            case "WEBP" -> WEBP;
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static String fileNameOf(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static RequestPostProcessor as(User user) {
        return user(new UserPrincipal(user));
    }

    private Order saveOrder(User assignedServicer) {
        Order newOrder = new Order();
        newOrder.setStatus(OrderStatus.IN_PROGRESS);
        newOrder.setAssignedServicer(assignedServicer);
        return orderRepository.save(newOrder);
    }

    private User saveUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("not-a-real-hash");
        user.setRole(role);
        user.setDisplayName("Test " + username);
        return userRepository.save(user);
    }
}
