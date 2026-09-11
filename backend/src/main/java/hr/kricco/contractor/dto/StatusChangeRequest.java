package hr.kricco.contractor.dto;

import hr.kricco.contractor.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(
        @NotNull OrderStatus status
) {
}
