package hr.kricco.contractor.dto;

import jakarta.validation.constraints.NotNull;

// servicerId must belong to an active SERVICER (checked in OrderService)
public record AssignmentRequest(
        @NotNull Long servicerId,
        @NotNull Long version
) {
}
