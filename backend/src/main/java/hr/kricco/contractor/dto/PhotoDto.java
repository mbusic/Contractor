package hr.kricco.contractor.dto;

// url is relative: /api/files/<filename>
public record PhotoDto(
        Long id,
        String url
) {
}
