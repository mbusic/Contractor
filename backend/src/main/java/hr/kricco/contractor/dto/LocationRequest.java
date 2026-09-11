package hr.kricco.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationRequest(
        @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 255) String city
) {
}
