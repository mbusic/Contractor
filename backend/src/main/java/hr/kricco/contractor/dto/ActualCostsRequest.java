package hr.kricco.contractor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// Body of PUT /api/orders/{id}/actual-costs. version is the order's version: the costs are columns of the order.
// A wrapper, so CostsRequest stays the same as in OrderRequest.estimatedCosts.
public record ActualCostsRequest(
        @Valid @NotNull CostsRequest costs,
        @NotNull Long version
) {
}
