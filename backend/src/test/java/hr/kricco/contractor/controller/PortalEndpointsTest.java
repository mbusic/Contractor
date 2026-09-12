package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderPhoto;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import hr.kricco.contractor.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.stream.Stream;

import static hr.kricco.contractor.TestUploads.JPEG;
import static hr.kricco.contractor.TestUploads.deleteFilesIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The client portal endpoints under /api/portal (in OrderController and ClientController).
// Runs against contractor_test, each test is rolled back.
// Requests run as a real client user (asClient), because the services work with the user's client.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.upload-dir}")
    private Path uploadDir;

    private Client company;
    private Location companySite;
    private Location otherClientSite;
    private User clientUser;
    private Order submitted;
    private Order portalDraft;
    private Order officeDraft;
    private Order otherClientsOrder;

    @BeforeEach
    void setUp() {
        company = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42", "Vukovarska 18");
        companySite = company.getLocations().getFirst();
        Client otherClient = saveClient("Ana Anić", "Flanatička 14");
        otherClientSite = otherClient.getLocations().getFirst();
        clientUser = saveClientUser(company);

        submitted = saveOrder(company, OrderStatus.PENDING, "T1", false);
        portalDraft = saveOrder(company, OrderStatus.DRAFT, null, true);
        officeDraft = saveOrder(company, OrderStatus.DRAFT, null, false);
        otherClientsOrder = saveOrder(otherClient, OrderStatus.PENDING, "T2", false);
    }

    @AfterEach
    void deleteUploadedFiles() {
        deleteFilesIn(uploadDir);
    }

    // Reading

    @Test
    void listShowsOwnSubmittedAndPortalOrdersNewestFirst() throws Exception {
        mockMvc.perform(get("/api/portal/orders").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(portalDraft.getId()))
                .andExpect(jsonPath("$[1].id").value(submitted.getId()))
                .andExpect(jsonPath("$[1].orderNumber").value("T1"))
                .andExpect(jsonPath("$[1].locationText").value("A.G. Matoša 42, Zagreb 10000"));
    }

    @Test
    void orderDetailHasNoInternalFields() throws Exception {
        mockMvc.perform(get("/api/portal/orders/{id}", submitted.getId()).with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("T1"))
                .andExpect(jsonPath("$.location.id").value(companySite.getId()))
                .andExpect(jsonPath("$.photos.length()").value(0))
                .andExpect(jsonPath("$.estimatedCosts").doesNotExist())
                .andExpect(jsonPath("$.actualCosts").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.assignedServicer").doesNotExist())
                .andExpect(jsonPath("$.branch").doesNotExist());
    }

    @Test
    void otherClientsOrderReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/portal/orders/{id}", otherClientsOrder.getId()).with(asClient()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't see this order"));
    }

    @Test
    void officeDraftReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/portal/orders/{id}", officeDraft.getId()).with(asClient()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/portal/orders/{id}", 999999).with(asClient()))
                .andExpect(status().isNotFound());
    }

    // Create and update

    @Test
    void createMakesPortalDraftForOwnClient() throws Exception {
        String body = mockMvc.perform(post("/api/portal/orders").with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationId": %d, "description": "Vrata se ne zatvaraju", "urgency": "ONE_WEEK"}
                                """.formatted(companySite.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.orderNumber").value(nullValue()))
                .andExpect(jsonPath("$.location.id").value(companySite.getId()))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
        Order created = orderRepository.findById(id).orElseThrow();
        assertThat(created.isCreatedInPortal()).isTrue();
        assertThat(created.getClient().getId()).isEqualTo(company.getId());
        assertThat(created.getBranch()).isNull();
    }

    @Test
    void createWithAnotherClientsLocationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/portal/orders").with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationId": %d}
                                """.formatted(otherClientSite.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Location not found"));
    }

    @Test
    void updateDraftReplacesFields() throws Exception {
        updateOrder(portalDraft, """
                {"description": "Nova verzija", "version": 0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Nova verzija"))
                .andExpect(jsonPath("$.location").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateSubmittedOrderReturnsConflict() throws Exception {
        updateOrder(submitted, """
                {"description": "Kasna izmjena", "version": 0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only a draft can be changed"));
    }

    @Test
    void updateWithStaleVersionReturnsConflict() throws Exception {
        portalDraft.setDescription("Saved by the office");
        orderRepository.saveAndFlush(portalDraft);

        updateOrder(portalDraft, """
                {"description": "Moja izmjena", "version": 0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    // Submit

    @Test
    void submitMakesDraftPendingWithNumber() throws Exception {
        portalDraft.setLocation(companySite);

        submit(portalDraft, portalDraft.getVersion())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderNumber").value(matchesPattern("\\d{3}/\\d{2}")));
    }

    @Test
    void submitWithoutLocationReturnsBadRequest() throws Exception {
        submit(portalDraft, portalDraft.getVersion())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Location is required"));
    }

    @Test
    void submitSubmittedOrderReturnsConflict() throws Exception {
        submit(submitted, submitted.getVersion())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only a draft can be changed"));
    }

    @Test
    void submitWithoutVersionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/portal/orders/{id}/submit", portalDraft.getId()).with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
    }

    // Photos

    @Test
    void addPhotoToDraft() throws Exception {
        uploadPhoto(portalDraft)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(1));
    }

    @Test
    void addPhotoToSubmittedOrderReturnsConflict() throws Exception {
        uploadPhoto(submitted)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only a draft can be changed"));
    }

    @Test
    void deletePhotoFromDraft() throws Exception {
        String body = uploadPhoto(portalDraft).andReturn().getResponse().getContentAsString();
        Integer photoId = com.jayway.jsonpath.JsonPath.read(body, "$.photos[0].id");

        mockMvc.perform(delete("/api/portal/orders/{id}/photos/{photoId}", portalDraft.getId(), photoId)
                        .with(asClient()))
                .andExpect(status().isNoContent());

        assertThat(portalDraft.getPhotos()).isEmpty();
    }

    @Test
    void deletePhotoFromSubmittedOrderReturnsConflict() throws Exception {
        OrderPhoto photo = new OrderPhoto();
        photo.setOrder(submitted);
        photo.setFilename("00000000-0000-0000-0000-000000000000.jpg");
        submitted.getPhotos().add(photo);
        orderRepository.saveAndFlush(submitted);
        // save() merges, so the collection holds the saved copy with the ID, not the photo object above
        Long photoId = submitted.getPhotos().getFirst().getId();

        mockMvc.perform(delete("/api/portal/orders/{id}/photos/{photoId}", submitted.getId(), photoId)
                        .with(asClient()))
                .andExpect(status().isConflict());
    }

    // Locations

    @Test
    void locationsAreTheOwnClientsOnly() throws Exception {
        mockMvc.perform(get("/api/portal/locations").with(asClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].address").value("A.G. Matoša 42"))
                .andExpect(jsonPath("$[1].address").value("Vukovarska 18"));
    }

    @Test
    void addLocationAddsItToOwnClient() throws Exception {
        mockMvc.perform(post("/api/portal/locations").with(asClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Vikendica", "address": "Obala 5", "city": "Split 21000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value("Obala 5"));

        assertThat(company.getLocations()).extracting(Location::getAddress).contains("Obala 5");
    }

    // Access by role: only CLIENT

    // Valid bodies on purpose: the body is validated before @PreAuthorize runs, so a bad body would give 400
    static Stream<MockHttpServletRequestBuilder> allEndpoints() {
        return Stream.of(
                get("/api/portal/orders"),
                get("/api/portal/orders/1"),
                post("/api/portal/orders").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/portal/orders/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/portal/orders/1/submit").contentType(MediaType.APPLICATION_JSON).content("""
                        {"version": 0}
                        """),
                multipart("/api/portal/orders/1/photos").file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG)),
                delete("/api/portal/orders/1/photos/1"),
                get("/api/portal/locations"),
                post("/api/portal/locations").contentType(MediaType.APPLICATION_JSON).content("""
                        {"address": "Obala 5", "city": "Split 21000"}
                        """));
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    @WithMockUser(roles = "ADMIN")
    void adminCannotCallPortalEndpoints(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    @WithMockUser(roles = "OFFICE")
    void officeCannotCallPortalEndpoints(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    @WithMockUser(roles = "SERVICER")
    void servicerCannotCallPortalEndpoints(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }

    private ResultActions updateOrder(Order order, String json) throws Exception {
        return mockMvc.perform(put("/api/portal/orders/{id}", order.getId()).with(asClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions submit(Order order, Long version) throws Exception {
        return mockMvc.perform(post("/api/portal/orders/{id}/submit", order.getId()).with(asClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d}
                        """.formatted(version)));
    }

    private ResultActions uploadPhoto(Order order) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG);
        return mockMvc.perform(multipart("/api/portal/orders/{id}/photos", order.getId()).file(file).with(asClient()));
    }

    private RequestPostProcessor asClient() {
        return user(new UserPrincipal(clientUser));
    }

    private Order saveOrder(Client client, OrderStatus status, String orderNumber, boolean createdInPortal) {
        Order order = new Order();
        order.setClient(client);
        order.setLocation(client.getLocations().getFirst());
        order.setStatus(status);
        order.setOrderNumber(orderNumber);
        order.setCreatedInPortal(createdInPortal);
        if (status == OrderStatus.DRAFT) {
            order.setLocation(null);
        }
        return orderRepository.save(order);
    }

    private Client saveClient(String name, String... addresses) {
        Client client = new Client();
        client.setType(ClientType.COMPANY);
        client.setName(name);
        for (String address : addresses) {
            Location location = new Location();
            location.setClient(client);
            location.setAddress(address);
            location.setCity("Zagreb 10000");
            client.getLocations().add(location);
        }
        return clientRepository.save(client);
    }

    private User saveClientUser(Client client) {
        User user = new User();
        user.setUsername("petar");
        user.setPassword("not-a-real-hash");
        user.setRole(Role.CLIENT);
        user.setDisplayName("Petar Perić");
        user.setClient(client);
        return userRepository.save(user);
    }
}
