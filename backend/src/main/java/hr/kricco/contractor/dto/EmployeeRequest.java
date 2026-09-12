package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// password: required on create. On update an empty password keeps the current one.
// Its 72-byte limit is checked in UserService, because @Size counts characters, not bytes.
// active: required, so a PUT that forgets it can't deactivate the account by accident.
// version: ignored on create, required on update (checked in the service, so one record serves both)
public record EmployeeRequest(
        @NotBlank @Size(max = 255) String username,
        String password,
        @NotNull Role role,
        @NotBlank @Size(max = 255) String displayName,
        Long branchId,
        @NotNull Boolean active,
        Long version
) {
}
