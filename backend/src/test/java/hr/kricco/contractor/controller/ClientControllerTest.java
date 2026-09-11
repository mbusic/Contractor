package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private LocationRepository locationRepository;

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

    @Test
    void updateReplacesAllFieldsAndKeepsLocations() throws Exception {
        Client client = saveClient("Ana Anić", "Flanatička 14");

        mockMvc.perform(put("/api/clients/{id}", client.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "INDIVIDUAL", "name": "Ana Anić Horvat"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.name").value("Ana Anić Horvat"))
                .andExpect(jsonPath("$.contactPerson").value(nullValue()))
                .andExpect(jsonPath("$.locations.length()").value(1));
    }

    @Test
    void deleteRemovesClientAndItsLocations() throws Exception {
        Client client = saveClient("Ana Anić", "Flanatička 14", "Ilica 10");

        mockMvc.perform(delete("/api/clients/{id}", client.getId()))
                .andExpect(status().isNoContent());

        assertThat(clientRepository.findById(client.getId())).isEmpty();
        assertThat(locationRepository.findAll()).isEmpty();
    }

    // Locations

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
                        .content(LOCATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Skladište"))
                .andExpect(jsonPath("$.address").value("Vukovarska 18"));
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
                delete("/api/clients/1/locations/1"));
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
}
