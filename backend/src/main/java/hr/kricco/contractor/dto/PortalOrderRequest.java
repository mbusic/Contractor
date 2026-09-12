package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.Urgency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// A client user's order form. The client is always the user's own, and there's no branch and no costs.
// All fields optional while the order is a DRAFT. The location must be one of the client's, and is required on submit.
// version: ignored on create, required on update (checked in the service, so one record serves both)
public record PortalOrderRequest(
        Long locationId,
        @Size(max = 255) String contactPerson,
        @Size(max = 255) String phone,
        @Email @Size(max = 255) String email,
        String description,
        Urgency urgency,
        Long version
) {
}
