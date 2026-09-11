package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against contractor_test. Each test is rolled back, so tests don't see each other's rows.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        User user = saveOfficeUser("office", "secret");

        login("office", "secret")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(notNullValue()))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.username").value("office"))
                .andExpect(jsonPath("$.role").value("OFFICE"))
                .andExpect(jsonPath("$.displayName").value("Dispečer"))
                .andExpect(jsonPath("$.branchId").value(user.getBranch().getId()));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        saveOfficeUser("office", "secret");

        login("office", "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void loginWithUnknownUsernameReturnsUnauthorized() throws Exception {
        login("nobody", "secret")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void loginWithoutPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "office"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    private User saveOfficeUser(String username, String password) {
        Branch branch = new Branch();
        branch.setName("Kricco Zagreb");
        branchRepository.save(branch);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.OFFICE);
        user.setDisplayName("Dispečer");
        user.setBranch(branch);
        return userRepository.save(user);
    }
}
