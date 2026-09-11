package hr.kricco.contractor.dto;

public record LocationDto(
        Long id,
        String name,
        String address,
        String city
) {
}
