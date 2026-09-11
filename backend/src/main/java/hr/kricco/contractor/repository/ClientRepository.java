package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
