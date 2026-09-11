package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // Empty if the location doesn't exist or belongs to another client
    Optional<Location> findByIdAndClientId(Long id, Long clientId);
}
