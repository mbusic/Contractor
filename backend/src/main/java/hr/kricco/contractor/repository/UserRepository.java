package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByBranchId(Long branchId);
}
