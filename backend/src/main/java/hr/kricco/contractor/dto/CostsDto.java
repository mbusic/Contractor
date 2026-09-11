package hr.kricco.contractor.dto;

import java.math.BigDecimal;

// totalHours is calculated: work hours x number of workers
public record CostsDto(
        Integer km,
        BigDecimal workHours,
        Integer numberOfWorkers,
        BigDecimal totalHours,
        BigDecimal materialCost
) {
}
