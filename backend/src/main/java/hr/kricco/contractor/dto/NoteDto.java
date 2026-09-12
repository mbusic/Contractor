package hr.kricco.contractor.dto;

import java.time.Instant;

public record NoteDto(
        Long id,
        String text,
        Long authorId,
        String authorName,
        Instant createdAt
) {
}
