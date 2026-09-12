package hr.kricco.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The role is always CLIENT and the client comes from the path.
// password: required on create. On update an empty password keeps the current one.
// Its 72-byte limit is checked in UserService, because @Size counts characters, not bytes.
// version: ignored on create, required on update (checked in the service, so one record serves both)
public record ClientUserRequest(
        @NotBlank @Size(max = 255) String username,
        String password,
        @NotBlank @Size(max = 255) String displayName,
        Long version
) {
}
