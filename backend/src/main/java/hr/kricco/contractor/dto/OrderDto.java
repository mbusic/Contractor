package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Urgency;

import java.time.Instant;
import java.util.List;

// The order detail. allowedNextStatuses is empty when the caller may not change the order.
// Actual costs, notes and photos come with their own slices.
public record OrderDto(
        Long id,
        String orderNumber,
        OrderStatus status,
        List<OrderStatus> allowedNextStatuses,
        Urgency urgency,
        BranchDto branch,
        ClientSummaryDto client,
        LocationDto location,
        String contactPerson,
        String phone,
        String email,
        String description,
        ServicerDto assignedServicer,
        CostsDto estimatedCosts,
        Instant createdAt,
        Instant updatedAt
) {
}
