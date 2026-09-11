package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    // For login and the token filter: a deactivated user counts as not found
    Optional<User> findByUsernameAndActiveTrue(String username);

    List<User> findByRoleIn(Collection<Role> roles, Sort sort);

    List<User> findByClientId(Long clientId, Sort sort);

    // Empty if the user doesn't exist or isn't a user of that client
    Optional<User> findByIdAndClientId(Long id, Long clientId);

    boolean existsByUsername(String username);

    boolean existsByBranchId(Long branchId);

    boolean existsByClientId(Long clientId);
}
