package hr.kricco.contractor;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Checks that seed.sql still runs against the current schema.sql.
// The script runs inside the test transaction, so everything is rolled back afterwards.
@SpringBootTest
@Transactional
@Sql("classpath:seed.sql")
class SeedDataTest {

    @Autowired
    private BranchRepository branchRepository;

    @Test
    void seedCreatesFiveBranches() {
        assertThat(branchRepository.findAll())
                .extracting(Branch::getCity)
                .containsExactlyInAnyOrder("Zagreb", "Split", "Zadar", "Osijek", "Pula");
    }

    @Test
    void seedStartsIdsFromOne() {
        Branch first = branchRepository.findById(1L).orElseThrow();

        assertThat(first.getName()).isEqualTo("Kricco Zagreb");
    }
}
