package hr.kricco.contractor.repository;

import hr.kricco.contractor.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // The @EntityGraph on the list queries loads branch, client, location and servicer in the same query,
    // so each row doesn't load them one by one.

    // All orders, for ADMIN and OFFICE. Newest first, the ID breaks ties between equal timestamps.
    @EntityGraph(attributePaths = {"branch", "client", "location", "assignedServicer"})
    List<Order> findAllByOrderByCreatedAtDescIdDesc();

    // For a SERVICER: orders assigned to them plus all unassigned PENDING orders, newest first
    @EntityGraph(attributePaths = {"branch", "client", "location", "assignedServicer"})
    @Query("""
            SELECT o FROM Order o
            WHERE o.assignedServicer.id = :servicerId
               OR (o.assignedServicer IS NULL AND o.status = hr.kricco.contractor.entity.OrderStatus.PENDING)
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    List<Order> findVisibleToServicer(@Param("servicerId") Long servicerId);

    boolean existsByBranchId(Long branchId);

    boolean existsByClientId(Long clientId);

    boolean existsByLocationId(Long locationId);
}
