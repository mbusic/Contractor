package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
}
