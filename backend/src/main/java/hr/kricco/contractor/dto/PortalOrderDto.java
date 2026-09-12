package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Urgency;

import java.time.Instant;
import java.util.List;

// The order as a client user sees it: no costs, notes, servicer or branch (they're internal)
public record PortalOrderDto(
        Long id,
        String orderNumber,
        OrderStatus status,
        Urgency urgency,
        LocationDto location,
        String contactPerson,
        String phone,
        String email,
        String description,
        List<PhotoDto> photos,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
