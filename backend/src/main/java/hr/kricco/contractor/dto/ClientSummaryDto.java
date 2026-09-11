package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.ClientType;

// The client as shown on an order, without its locations
public record ClientSummaryDto(
        Long id,
        ClientType type,
        String name
) {
}
