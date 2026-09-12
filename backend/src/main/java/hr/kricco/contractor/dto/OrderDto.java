package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Urgency;

import java.time.Instant;
import java.util.List;

// The order detail. allowedNextStatuses is empty when the caller may not change the order.
// costDifference is actual - estimated per field, null where either value is missing.
// notes are newest first. Photos come with their own slice.
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
        CostsDto actualCosts,
        CostsDto costDifference,
        List<NoteDto> notes,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
