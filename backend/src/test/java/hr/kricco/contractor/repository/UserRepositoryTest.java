package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs against contractor_test. Each test is rolled back, so tests don't see each other's rows.
@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Test
    void findByUsernameReturnsUserWithBranch() {
        Branch zagreb = saveBranch("Kricco Zagreb");
        userRepository.save(newUser("office", Role.OFFICE, zagreb));

        User found = userRepository.findByUsername("office").orElseThrow();

        assertThat(found.getRole()).isEqualTo(Role.OFFICE);
        assertThat(found.getBranch().getName()).isEqualTo("Kricco Zagreb");
    }

    @Test
    void findByUnknownUsernameReturnsEmpty() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void duplicateUsernameIsRejected() {
        userRepository.saveAndFlush(newUser("admin", Role.ADMIN, null));
        User duplicate = newUser("admin", Role.ADMIN, null);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByBranchIdFindsUsersOfThatBranchOnly() {
        Branch zagreb = saveBranch("Kricco Zagreb");
        Branch split = saveBranch("Kricco Split");
        userRepository.save(newUser("servicer", Role.SERVICER, zagreb));

        assertThat(userRepository.existsByBranchId(zagreb.getId())).isTrue();
        assertThat(userRepository.existsByBranchId(split.getId())).isFalse();
    }

    private Branch saveBranch(String name) {
        Branch branch = new Branch();
        branch.setName(name);
        return branchRepository.save(branch);
    }

    private User newUser(String username, Role role, Branch branch) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("not-a-real-hash");
        user.setRole(role);
        user.setDisplayName("Test " + username);
        user.setBranch(branch);
        return user;
    }
}
