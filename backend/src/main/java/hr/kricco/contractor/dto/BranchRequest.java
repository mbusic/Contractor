package hr.kricco.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String city
) {
}
