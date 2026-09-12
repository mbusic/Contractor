package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.ClientType;

import java.util.List;

public record ClientDto(
        Long id,
        ClientType type,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        List<LocationDto> locations,
        Long version
) {
}
