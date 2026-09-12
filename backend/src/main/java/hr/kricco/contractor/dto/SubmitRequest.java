package hr.kricco.contractor.dto;

import jakarta.validation.constraints.NotNull;

// Body of POST /api/portal/orders/{id}/submit. If the office changed the draft since the client opened it, 409.
public record SubmitRequest(
        @NotNull Long version
) {
}
