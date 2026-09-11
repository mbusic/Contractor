package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.Role;

// branchId is set for OFFICE and SERVICER, clientId for CLIENT
public record LoginResponse(
        String token,
        Long userId,
        String username,
        Role role,
        String displayName,
        Long branchId,
        Long clientId
) {
}
