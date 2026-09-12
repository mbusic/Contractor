package hr.kricco.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// version: ignored on create, required on update (checked in the service, so one record serves both)
public record BranchRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String city,
        Long version
) {
}
