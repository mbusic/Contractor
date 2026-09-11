package hr.kricco.contractor.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Estimated or actual costs of an order. Order uses it twice, with its own column names.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Costs {

    private Integer km;

    // Hours per worker
    private BigDecimal workHours;

    private Integer numberOfWorkers;

    // EUR
    private BigDecimal materialCost;

    // Work hours x number of workers, calculated, not stored (domain-model C6). Null if either is missing.
    public BigDecimal getTotalHours() {
        if (workHours == null || numberOfWorkers == null) {
            return null;
        }
        return workHours.multiply(BigDecimal.valueOf(numberOfWorkers));
    }
}
