package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.Role;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        Role role,
        String displayName,
        Long branchId
) {
}
