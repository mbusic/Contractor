package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Urgency;

import java.time.Instant;

// One row of the portal order list. locationText is "address, city".
public record PortalOrderSummaryDto(
        Long id,
        String orderNumber,
        OrderStatus status,
        Urgency urgency,
        String locationText,
        Instant createdAt
) {
}
