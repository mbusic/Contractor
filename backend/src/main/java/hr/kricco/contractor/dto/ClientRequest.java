package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.ClientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Locations are not part of it, they have their own endpoints
public record ClientRequest(
        @NotNull ClientType type,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String contactPerson,
        @Size(max = 255) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String address
) {
}
