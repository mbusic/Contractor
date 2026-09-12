package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.LocationRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against contractor_test. Each test is rolled back, so tests don't see each other's rows.
// OFFICE by default (ADMIN and OFFICE have the same access here). The access tests at the end override the role.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "OFFICE")
class ClientControllerTest {

    private static final String CLIENT_JSON = """
            {"type": "COMPANY", "name": "Petar Perić d.o.o.", "contactPerson": "Petar Perić",
             "phone": "097 587 6210", "email": "petar.peric@example.hr", "address": "Savska 1, Zagreb 10000"}
            """;

    private static final String LOCATION_JSON = """
            {"name": "Skladište", "address": "Vukovarska 18", "city": "Split 21000"}
            """;

    private static final String CLIENT_USER_JSON = """
            {"username": "petar", "password": "tajna", "displayName": "Petar Perić"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Clients

    @Test
    void getAllReturnsClientsSortedByNameWithLocations() throws Exception {
        saveClient("Petar Perić d.o.o.", "A.G. Matoša 42", "Vukovarska 18");
        saveClient("Ana Anić", "Flanatička 14");

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ana Anić"))
                .andExpect(jsonPath("$[0].locations.length()").value(1))
                .andExpect(jsonPath("$[1].name").value("Petar Perić d.o.o."))
                .andExpect(jsonPath("$[1].locations[0].address").value("A.G. Matoša 42"))
                .andExpect(jsonPath("$[1].locations[1].address").value("Vukovarska 18"));
    }

    @Test
    void getByIdReturnsClient() throws Exception {
        Client client = saveClient("Ana Anić", "Flanatička 14");

        mockMvc.perform(get("/api/clients/{id}", client.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Anić"))
                .andExpect(jsonPath("$.type").value("COMPANY"))
                .andExpect(jsonPath("$.locations[0].city").value("Zagreb 10000"));
    }

    @Test
    void getByUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/clients/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client not found"));
    }

    @Test
    void createReturnsNewClient() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.type").value("COMPANY"))
                .andExpect(jsonPath("$.name").value("Petar Perić d.o.o."))
                .andExpect(jsonPath("$.email").value("petar.peric@example.hr"))
                .andExpect(jsonPath("$.address").value("Savska 1, Zagreb 10000"))
                .andExpect(jsonPath("$.locations.length()").value(0));
    }

    @Test
    void createWithoutNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "INDIVIDUAL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithInvalidEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "INDIVIDUAL", "name": "Ana Anić", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // Pins the fieldErrors format that every Bean Validation error uses: one entry per field, sorted by field
    @Test
    void invalidFieldsAreListedInFieldErrors() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "INDIVIDUAL", "name": "", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andExpect(jsonPath("$.instance").value("/api/clients"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a well-formed email address"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("must not be blank"));
    }

    @Test
    void updateReplacesAllFieldsAndKeepsLocations() throws Exception {
        Client client = saveClient("Ana Anić", "Flanatička 14");

        mockMvc.perform(put("/api/clients/{id}", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "INDIVIDUAL", "name": "Ana Anić Horvat", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.name").value("Ana Anić Horvat"))
                .andExpect(jsonPath("$.contactPerson").value(nullValue()))
                .andExpect(jsonPath("$.locations.length()").value(1))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateWithStaleVersionReturnsConflict() throws Exception {
        Client client = saveClient("Ana Anić");
        client.setPhone("Saved by someone else");
        clientRepository.saveAndFlush(client);

        mockMvc.perform(put("/api/clients/{id}", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "INDIVIDUAL", "name": "Ana Anić Horvat", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    @Test
    void deleteRemovesClientAndItsLocations() throws Exception {
        Client client = saveClient("Ana Anić", "Flanatička 14", "Ilica 10");

        mockMvc.perform(delete("/api/clients/{id}", client.getId()))
                .andExpect(status().isNoContent());

        assertThat(clientRepository.findById(client.getId())).isEmpty();
        assertThat(locationRepository.findAll()).isEmpty();
    }

    @Test
    void deleteClientWithUsersReturnsConflict() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");
        saveClientUser(client, "petar");

        mockMvc.perform(delete("/api/clients/{id}", client.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Client has users"));

        assertThat(clientRepository.findById(client.getId())).isPresent();
    }

    @Test
    void deleteClientWithOrdersReturnsConflict() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        saveOrderAt(client, client.getLocations().getFirst());

        mockMvc.perform(delete("/api/clients/{id}", client.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Client has orders"));
    }

    // Locations

    @Test
    void deleteLocationUsedByOrderReturnsConflict() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        Location location = client.getLocations().getFirst();
        saveOrderAt(client, location);

        mockMvc.perform(delete("/api/clients/{id}/locations/{locationId}", client.getId(), location.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Location is used by orders"));

        assertThat(locationRepository.findById(location.getId())).isPresent();
    }

    @Test
    void addLocationReturnsNewLocation() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");

        mockMvc.perform(post("/api/clients/{id}/locations", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.name").value("Skladište"))
                .andExpect(jsonPath("$.address").value("Vukovarska 18"))
                .andExpect(jsonPath("$.city").value("Split 21000"));

        mockMvc.perform(get("/api/clients/{id}", client.getId()))
                .andExpect(jsonPath("$.locations.length()").value(1));
    }

    @Test
    void addLocationWithoutCityReturnsBadRequest() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");

        mockMvc.perform(post("/api/clients/{id}/locations", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address": "Vukovarska 18"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addLocationToUnknownClientReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/clients/{id}/locations", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client not found"));
    }

    @Test
    void updateLocationReplacesAllFields() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        Location location = client.getLocations().getFirst();

        mockMvc.perform(put("/api/clients/{id}/locations/{locationId}", client.getId(), location.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Skladište", "address": "Vukovarska 18", "city": "Split 21000", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Skladište"))
                .andExpect(jsonPath("$.address").value("Vukovarska 18"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateLocationWithStaleVersionReturnsConflict() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        Location location = client.getLocations().getFirst();
        location.setName("Saved by someone else");
        locationRepository.saveAndFlush(location);

        mockMvc.perform(put("/api/clients/{id}/locations/{locationId}", client.getId(), location.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Skladište", "address": "Vukovarska 18", "city": "Split 21000", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    @Test
    void updateLocationOfAnotherClientReturnsNotFound() throws Exception {
        Client owner = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        Client other = saveClient("Ana Anić");
        Location location = owner.getLocations().getFirst();

        mockMvc.perform(put("/api/clients/{id}/locations/{locationId}", other.getId(), location.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Location not found"));
    }

    @Test
    void deleteLocationRemovesIt() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42", "Vukovarska 18");
        Location location = client.getLocations().getFirst();

        mockMvc.perform(delete("/api/clients/{id}/locations/{locationId}", client.getId(), location.getId()))
                .andExpect(status().isNoContent());

        locationRepository.flush();
        assertThat(locationRepository.findById(location.getId())).isEmpty();
        assertThat(locationRepository.findAll()).hasSize(1);
    }

    @Test
    void deleteLocationOfAnotherClientReturnsNotFound() throws Exception {
        Client owner = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        Client other = saveClient("Ana Anić");
        Location location = owner.getLocations().getFirst();

        mockMvc.perform(delete("/api/clients/{id}/locations/{locationId}", other.getId(), location.getId()))
                .andExpect(status().isNotFound());

        assertThat(locationRepository.findById(location.getId())).isPresent();
    }

    // Client users

    @Test
    void getUsersReturnsOnlyThisClientsUsersSortedByName() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");
        Client other = saveClient("Ana Anić");
        saveClientUser(client, "zvonko");
        saveClientUser(client, "petar");
        saveClientUser(other, "ana");

        mockMvc.perform(get("/api/clients/{id}/users", client.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("petar"))
                .andExpect(jsonPath("$[1].username").value("zvonko"));
    }

    @Test
    void getUsersOfUnknownClientReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/clients/{id}/users", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client not found"));
    }

    @Test
    void createUserReturnsClientUserWithHashedPassword() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");

        mockMvc.perform(post("/api/clients/{id}/users", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_USER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.username").value("petar"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.displayName").value("Petar Perić"))
                .andExpect(jsonPath("$.clientId").value(client.getId()))
                .andExpect(jsonPath("$.clientName").value("Petar Perić d.o.o."))
                .andExpect(jsonPath("$.branchId").value(nullValue()))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByUsername("petar").orElseThrow();
        assertThat(passwordEncoder.matches("tajna", saved.getPassword())).isTrue();
    }

    @Test
    void createUserWithUsernameOfAnEmployeeReturnsConflict() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");
        User employee = new User();
        employee.setUsername("petar");
        employee.setPassword("not-a-real-hash");
        employee.setRole(Role.ADMIN);
        employee.setDisplayName("Petar Admin");
        userRepository.save(employee);

        mockMvc.perform(post("/api/clients/{id}/users", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_USER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username is taken"));
    }

    @Test
    void createUserWithoutPasswordReturnsBadRequest() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");

        mockMvc.perform(post("/api/clients/{id}/users", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "petar", "displayName": "Petar Perić"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password is required"));
    }

    @Test
    void updateUserReplacesFieldsAndKeepsPasswordWhenEmpty() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");
        User user = saveClientUser(client, "petar");
        String oldHash = user.getPassword();

        mockMvc.perform(put("/api/clients/{id}/users/{userId}", client.getId(), user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "petar2", "password": "", "displayName": "Petar P.", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("petar2"))
                .andExpect(jsonPath("$.displayName").value("Petar P."))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPassword()).isEqualTo(oldHash);
    }

    @Test
    void updateUserWithStaleVersionReturnsConflict() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");
        User user = saveClientUser(client, "petar");
        user.setDisplayName("Saved by someone else");
        userRepository.saveAndFlush(user);

        mockMvc.perform(put("/api/clients/{id}/users/{userId}", client.getId(), user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "petar2", "password": "", "displayName": "Petar P.", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    @Test
    void updateUserOfAnotherClientReturnsNotFound() throws Exception {
        Client owner = saveClient("Petar Perić d.o.o.");
        Client other = saveClient("Ana Anić");
        User user = saveClientUser(owner, "petar");

        mockMvc.perform(put("/api/clients/{id}/users/{userId}", other.getId(), user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_USER_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client user not found"));
    }

    @Test
    void deleteUserRemovesIt() throws Exception {
        Client client = saveClient("Petar Perić d.o.o.");
        User user = saveClientUser(client, "petar");

        mockMvc.perform(delete("/api/clients/{id}/users/{userId}", client.getId(), user.getId()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void deleteUserOfAnotherClientReturnsNotFound() throws Exception {
        Client owner = saveClient("Petar Perić d.o.o.");
        Client other = saveClient("Ana Anić");
        User user = saveClientUser(owner, "petar");

        mockMvc.perform(delete("/api/clients/{id}/users/{userId}", other.getId(), user.getId()))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    // Access by role: ADMIN and OFFICE only

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadClients() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientUserCannotReadClients() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isForbidden());
    }

    // Valid bodies on purpose: the body is validated before @PreAuthorize runs, so a bad body would give 400
    static Stream<MockHttpServletRequestBuilder> allEndpoints() {
        return Stream.of(
                get("/api/clients"),
                get("/api/clients/1"),
                post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(CLIENT_JSON),
                put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(CLIENT_JSON),
                delete("/api/clients/1"),
                post("/api/clients/1/locations").contentType(MediaType.APPLICATION_JSON).content(LOCATION_JSON),
                put("/api/clients/1/locations/1").contentType(MediaType.APPLICATION_JSON).content(LOCATION_JSON),
                delete("/api/clients/1/locations/1"),
                get("/api/clients/1/users"),
                post("/api/clients/1/users").contentType(MediaType.APPLICATION_JSON).content(CLIENT_USER_JSON),
                put("/api/clients/1/users/1").contentType(MediaType.APPLICATION_JSON).content(CLIENT_USER_JSON),
                delete("/api/clients/1/users/1"));
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    @WithMockUser(roles = "SERVICER")
    void servicerCannotCallClientEndpoints(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }

    // All test locations are in Zagreb, only the address differs
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

    private void saveOrderAt(Client client, Location location) {
        Order order = new Order();
        order.setStatus(OrderStatus.DRAFT);
        order.setClient(client);
        order.setLocation(location);
        orderRepository.save(order);
    }

    private User saveClientUser(Client client, String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("secret"));
        user.setRole(Role.CLIENT);
        user.setDisplayName("Test " + username);
        user.setClient(client);
        return userRepository.save(user);
    }
}
