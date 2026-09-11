package hr.kricco.contractor.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// All fields optional. The @Digits limits match the NUMERIC columns, so a too big value is a 400, not a 500.
public record CostsRequest(
        @PositiveOrZero Integer km,
        @PositiveOrZero @Digits(integer = 4, fraction = 2) BigDecimal workHours,
        @PositiveOrZero Integer numberOfWorkers,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal materialCost
) {
}
