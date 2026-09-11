package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.Role;

public record UserDto(
        Long id,
        String username,
        Role role,
        String displayName,
        Long branchId,
        String branchName,
        boolean active
) {
}
