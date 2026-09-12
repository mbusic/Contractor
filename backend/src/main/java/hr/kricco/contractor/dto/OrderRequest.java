package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.Urgency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// All fields optional while the order is a DRAFT.
// Client and location are required once the order is PENDING, IN_PROGRESS or RESOLVED (checked in OrderService).
// version: ignored on create, required on update (checked in the service, so one record serves both)
public record OrderRequest(
        Long branchId,
        Long clientId,
        Long locationId,
        @Size(max = 255) String contactPerson,
        @Size(max = 255) String phone,
        @Email @Size(max = 255) String email,
        String description,
        Urgency urgency,
        @Valid CostsRequest estimatedCosts,
        Long version
) {
}
