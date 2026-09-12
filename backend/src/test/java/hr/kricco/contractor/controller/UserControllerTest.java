package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import hr.kricco.contractor.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against contractor_test. Each test is rolled back, so tests don't see each other's rows.
// Most requests run as a real admin (asAdmin), because update and delete need the UserPrincipal.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Branch zagreb;
    private User admin;

    @BeforeEach
    void setUp() {
        zagreb = new Branch();
        zagreb.setName("Kricco Zagreb");
        branchRepository.save(zagreb);

        admin = saveUser("admin", Role.ADMIN, null);
    }

    // List

    @Test
    void getEmployeesReturnsActiveAndDeactivatedSortedByName() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);
        servicer.setActive(false);

        mockMvc.perform(asAdmin(get("/api/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].branchId").doesNotExist())
                .andExpect(jsonPath("$[1].username").value("servicer"))
                .andExpect(jsonPath("$[1].branchName").value("Kricco Zagreb"))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void getEmployeesFiltersByRole() throws Exception {
        saveUser("servicer", Role.SERVICER, zagreb);
        saveUser("office", Role.OFFICE, zagreb);

        mockMvc.perform(asAdmin(get("/api/users").param("role", "SERVICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("servicer"));
    }

    @Test
    void getEmployeesLeavesOutClientUsers() throws Exception {
        saveClientUser("petar");

        mockMvc.perform(asAdmin(get("/api/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void updateClientUserThroughEmployeeEndpointReturnsNotFound() throws Exception {
        User clientUser = saveClientUser("petar");

        mockMvc.perform(asAdmin(put("/api/users/{id}", clientUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("petar", "", "OFFICE", zagreb.getId(), true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee not found"));
    }

    @Test
    void getEmployeesWithClientRoleReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(get("/api/users").param("role", "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Role must be ADMIN, OFFICE or SERVICER"));
    }

    // Create

    @Test
    void createReturnsNewEmployeeWithHashedPassword() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ivan", "tajna", "SERVICER", zagreb.getId(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.username").value("ivan"))
                .andExpect(jsonPath("$.role").value("SERVICER"))
                .andExpect(jsonPath("$.branchId").value(zagreb.getId()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByUsername("ivan").orElseThrow();
        assertThat(passwordEncoder.matches("tajna", saved.getPassword())).isTrue();
    }

    @Test
    void createWithTakenUsernameReturnsConflict() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("admin", "tajna", "ADMIN", null, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username is taken"));
    }

    @Test
    void createWithoutPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ivan", null, "SERVICER", zagreb.getId(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password is required"));
    }

    // 37 x "č" is 74 bytes: under a 72-character @Size limit, but over BCrypt's 72 bytes
    @Test
    void createWithPasswordOver72BytesReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ivan", "č".repeat(37), "SERVICER", zagreb.getId(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password is too long"));
    }

    @Test
    void createServicerWithoutBranchReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ivan", "tajna", "SERVICER", null, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Branch is required for SERVICER"));
    }

    @Test
    void createAdminWithBranchReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("boss", "tajna", "ADMIN", zagreb.getId(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("An admin has no branch"));
    }

    @Test
    void createWithUnknownBranchReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ivan", "tajna", "SERVICER", 999999L, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Branch not found"));
    }

    @Test
    void createWithClientRoleReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("petar", "tajna", "CLIENT", null, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Role must be ADMIN, OFFICE or SERVICER"));
    }

    @Test
    void createWithoutActiveReturnsBadRequest() throws Exception {
        mockMvc.perform(asAdmin(post("/api/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ivan", "password": "tajna", "role": "ADMIN", "displayName": "Ivan"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // Update

    @Test
    void updateReplacesFieldsAndKeepsPasswordWhenEmpty() throws Exception {
        User office = saveUser("office", Role.OFFICE, zagreb);
        String oldHash = office.getPassword();

        mockMvc.perform(asAdmin(put("/api/users/{id}", office.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("office2", "", "ADMIN", null, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("office2"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.branchId").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));

        assertThat(userRepository.findById(office.getId()).orElseThrow().getPassword()).isEqualTo(oldHash);
    }

    @Test
    void updateWithStaleVersionReturnsConflict() throws Exception {
        User office = saveUser("office", Role.OFFICE, zagreb);
        office.setDisplayName("Saved by someone else");
        userRepository.saveAndFlush(office);

        mockMvc.perform(asAdmin(put("/api/users/{id}", office.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("office", "", "OFFICE", zagreb.getId(), true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    @Test
    void updateWithPasswordChangesIt() throws Exception {
        User office = saveUser("office", Role.OFFICE, zagreb);

        mockMvc.perform(asAdmin(put("/api/users/{id}", office.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("office", "nova", "OFFICE", zagreb.getId(), true)))
                .andExpect(status().isOk());

        User updated = userRepository.findById(office.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("nova", updated.getPassword())).isTrue();
    }

    @Test
    void updateToTakenUsernameReturnsConflict() throws Exception {
        User office = saveUser("office", Role.OFFICE, zagreb);

        mockMvc.perform(asAdmin(put("/api/users/{id}", office.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("admin", "", "OFFICE", zagreb.getId(), true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username is taken"));
    }

    @Test
    void updateCanReactivateEmployee() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);
        servicer.setActive(false);

        mockMvc.perform(asAdmin(put("/api/users/{id}", servicer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("servicer", "", "SERVICER", zagreb.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateOwnRoleAwayFromAdminReturnsConflict() throws Exception {
        mockMvc.perform(asAdmin(put("/api/users/{id}", admin.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("admin", "", "OFFICE", zagreb.getId(), true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can't remove your own admin role"));
    }

    @Test
    void updateOwnAccountToInactiveReturnsConflict() throws Exception {
        mockMvc.perform(asAdmin(put("/api/users/{id}", admin.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("admin", "", "ADMIN", null, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can't deactivate your own account"));
    }

    @Test
    void updateUnknownEmployeeReturnsNotFound() throws Exception {
        mockMvc.perform(asAdmin(put("/api/users/{id}", 999999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ghost", "", "ADMIN", null, true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee not found"));
    }

    // Delete = deactivate

    @Test
    void deleteDeactivatesEmployee() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);

        mockMvc.perform(asAdmin(delete("/api/users/{id}", servicer.getId())))
                .andExpect(status().isNoContent());

        User deactivated = userRepository.findById(servicer.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void deleteOwnAccountReturnsConflict() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/users/{id}", admin.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can't deactivate your own account"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    // A servicer who stops being an active SERVICER releases their IN_PROGRESS orders

    @Test
    void deactivatingServicerReleasesTheirInProgressOrders() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);
        Order inProgress = saveOrder(OrderStatus.IN_PROGRESS, servicer);
        Order resolved = saveOrder(OrderStatus.RESOLVED, servicer);

        mockMvc.perform(asAdmin(delete("/api/users/{id}", servicer.getId())))
                .andExpect(status().isNoContent());

        assertThat(inProgress.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(inProgress.getAssignedServicer()).isNull();
        assertThat(resolved.getAssignedServicer()).isEqualTo(servicer);
    }

    @Test
    void updateToInactiveReleasesServicerOrders() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);
        Order inProgress = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        mockMvc.perform(asAdmin(put("/api/users/{id}", servicer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("servicer", "", "SERVICER", zagreb.getId(), false)))
                .andExpect(status().isOk());

        assertThat(inProgress.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(inProgress.getAssignedServicer()).isNull();
    }

    @Test
    void changingServicerRoleReleasesTheirOrders() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);
        Order inProgress = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        mockMvc.perform(asAdmin(put("/api/users/{id}", servicer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("servicer", "", "OFFICE", zagreb.getId(), true)))
                .andExpect(status().isOk());

        assertThat(inProgress.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(inProgress.getAssignedServicer()).isNull();
    }

    @Test
    void updateOfActiveServicerKeepsTheirOrders() throws Exception {
        User servicer = saveUser("servicer", Role.SERVICER, zagreb);
        Order inProgress = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        mockMvc.perform(asAdmin(put("/api/users/{id}", servicer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("servicer2", "", "SERVICER", zagreb.getId(), true)))
                .andExpect(status().isOk());

        assertThat(inProgress.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(inProgress.getAssignedServicer()).isEqualTo(servicer);
    }

    // Access by role: ADMIN and OFFICE read, only ADMIN writes

    @Test
    @WithMockUser(roles = "OFFICE")
    void officeCanReadEmployees() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SERVICER")
    void servicerCannotReadEmployees() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotReadEmployees() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICE")
    void officeCannotCreateEmployee() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("ivan", "tajna", "SERVICER", zagreb.getId(), true)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICE")
    void officeCannotUpdateEmployee() throws Exception {
        mockMvc.perform(put("/api/users/{id}", admin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("admin", "", "ADMIN", null, true)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICE")
    void officeCannotDeactivateEmployee() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", admin.getId()))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(user(new UserPrincipal(admin)));
    }

    // version 0 matches a user saved once in the test. Create ignores it.
    private String employeeJson(String username, String password, String role, Long branchId, boolean active) {
        String passwordValue = password == null ? "null" : "\"" + password + "\"";
        String branchValue = branchId == null ? "null" : branchId.toString();
        return """
                {"username": "%s", "password": %s, "role": "%s", "displayName": "Test %s", "branchId": %s, "active": %s, "version": 0}
                """.formatted(username, passwordValue, role, username, branchValue, active);
    }

    private User saveClientUser(String username) {
        Client client = new Client();
        client.setType(ClientType.COMPANY);
        client.setName("Petar Perić d.o.o.");
        clientRepository.save(client);

        User user = saveUser(username, Role.CLIENT, null);
        user.setClient(client);
        return user;
    }

    // A submitted order needs a client and a location, also when it goes back to PENDING
    private Order saveOrder(OrderStatus status, User servicer) {
        Client client = new Client();
        client.setType(ClientType.COMPANY);
        client.setName("Petar Perić d.o.o.");
        Location location = new Location();
        location.setClient(client);
        location.setAddress("A.G. Matoša 42");
        location.setCity("Zagreb 10000");
        client.getLocations().add(location);
        clientRepository.save(client);

        Order order = new Order();
        order.setStatus(status);
        order.setClient(client);
        order.setLocation(location);
        order.setOrderNumber("T" + System.nanoTime());
        order.setAssignedServicer(servicer);
        return orderRepository.save(order);
    }

    private User saveUser(String username, Role role, Branch branch) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("secret"));
        user.setRole(role);
        user.setDisplayName("Test " + username);
        user.setBranch(branch);
        return userRepository.save(user);
    }
}
