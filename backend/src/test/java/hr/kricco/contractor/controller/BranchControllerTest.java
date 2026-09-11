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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
// @WithMockUser logs in a fake user, so the tests skip the JWT (covered by JwtAuthFilterTest).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class BranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createReturnsNewBranch() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Kricco Zagreb", "city": "Zagreb"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.name").value("Kricco Zagreb"))
                .andExpect(jsonPath("$.city").value("Zagreb"));
    }

    @Test
    void createWithoutNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city": "Zagreb"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllReturnsBranchesSortedByName() throws Exception {
        saveBranch("Kricco Zagreb", "Zagreb");
        saveBranch("Kricco Split", "Split");

        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Kricco Split"))
                .andExpect(jsonPath("$[1].name").value("Kricco Zagreb"));
    }

    @Test
    void getByIdReturnsBranch() throws Exception {
        Branch branch = saveBranch("Kricco Osijek", "Osijek");

        mockMvc.perform(get("/api/branches/{id}", branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kricco Osijek"));
    }

    // Also pins the Problem Details format that every error uses
    @Test
    void getByUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/branches/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Branch not found"))
                .andExpect(jsonPath("$.instance").value("/api/branches/999999"));
    }

    @Test
    void updateReplacesAllFields() throws Exception {
        Branch branch = saveBranch("Kricco Zadar", "Zadar");

        mockMvc.perform(put("/api/branches/{id}", branch.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Kricco Zadar 2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kricco Zadar 2"))
                .andExpect(jsonPath("$.city").value(nullValue()));
    }

    @Test
    void deleteRemovesBranch() throws Exception {
        Branch branch = saveBranch("Kricco Pula", "Pula");

        mockMvc.perform(delete("/api/branches/{id}", branch.getId()))
                .andExpect(status().isNoContent());

        assertThat(branchRepository.findById(branch.getId())).isEmpty();
    }

    @Test
    void deleteBranchWithUsersReturnsConflict() throws Exception {
        Branch branch = saveBranch("Kricco Split", "Split");
        saveServicer(branch);

        mockMvc.perform(delete("/api/branches/{id}", branch.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Branch has users"));

        assertThat(branchRepository.findById(branch.getId())).isPresent();
    }

    private Branch saveBranch(String name, String city) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setCity(city);
        return branchRepository.save(branch);
    }

    private void saveServicer(Branch branch) {
        User servicer = new User();
        servicer.setUsername("servicer");
        servicer.setPassword("not-a-real-hash");
        servicer.setRole(Role.SERVICER);
        servicer.setDisplayName("Ivan Horvat");
        servicer.setBranch(branch);
        userRepository.save(servicer);
    }
}
