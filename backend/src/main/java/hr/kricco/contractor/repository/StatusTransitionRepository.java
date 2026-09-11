package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.StatusTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusTransitionRepository extends JpaRepository<StatusTransition, Long> {

    boolean existsByFromStatusAndToStatus(OrderStatus fromStatus, OrderStatus toStatus);

    List<StatusTransition> findByFromStatus(OrderStatus fromStatus);
}
