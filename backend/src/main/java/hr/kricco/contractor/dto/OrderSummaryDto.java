package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Urgency;

import java.time.Instant;

// One row of the order list. locationText is the location as one line: "address, city".
public record OrderSummaryDto(
        Long id,
        String orderNumber,
        OrderStatus status,
        Urgency urgency,
        Long branchId,
        String branchName,
        String clientName,
        String locationText,
        Long assignedServicerId,
        String assignedServicerName,
        Instant createdAt
) {
}
