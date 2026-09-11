package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

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

    @Test
    void getByUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/branches/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Branch not found"));
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

    private Branch saveBranch(String name, String city) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setCity(city);
        return branchRepository.save(branch);
    }
}
