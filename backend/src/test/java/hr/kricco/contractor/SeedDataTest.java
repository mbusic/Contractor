package hr.kricco.contractor;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// Checks that seed.sql still runs against the current schema.sql.
// The script runs inside the test transaction, so everything is rolled back afterwards.
@SpringBootTest
@Transactional
@Sql("classpath:seed.sql")
class SeedDataTest {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    void seedCreatesOneUserPerEmployeeRole() {
        assertThat(userRepository.findAll())
                .extracting(User::getUsername, User::getRole)
                .containsExactlyInAnyOrder(
                        tuple("admin", Role.ADMIN),
                        tuple("office", Role.OFFICE),
                        tuple("servicer", Role.SERVICER));
    }

    @Test
    void seedPutsOfficeAndServicerInZagreb() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User office = userRepository.findByUsername("office").orElseThrow();
        User servicer = userRepository.findByUsername("servicer").orElseThrow();

        assertThat(admin.getBranch()).isNull();
        assertThat(office.getBranch().getCity()).isEqualTo("Zagreb");
        assertThat(servicer.getBranch().getCity()).isEqualTo("Zagreb");
    }

    @Test
    void seedPasswordsMatchUsernames() {
        for (String username : List.of("admin", "office", "servicer")) {
            User user = userRepository.findByUsername(username).orElseThrow();

            assertThat(passwordEncoder.matches(username, user.getPassword()))
                    .as("password of %s", username)
                    .isTrue();
        }
    }

    @Test
    void seedCreatesTwoClientsWithTheirLocations() {
        Client company = clientRepository.findById(1L).orElseThrow();
        Client individual = clientRepository.findById(2L).orElseThrow();

        assertThat(company.getType()).isEqualTo(ClientType.COMPANY);
        assertThat(company.getLocations()).extracting(Location::getCity)
                .containsExactly("Zagreb 10000", "Split 21000", "Osijek 31000");
        assertThat(individual.getType()).isEqualTo(ClientType.INDIVIDUAL);
        assertThat(individual.getLocations()).extracting(Location::getCity)
                .containsExactly("Zadar 23000", "Pula 52100");
    }

    // Catches a wrong file encoding when the script is read
    @Test
    void seedKeepsCroatianLetters() {
        User office = userRepository.findByUsername("office").orElseThrow();

        assertThat(office.getDisplayName()).isEqualTo("Dispečer");
    }
}
