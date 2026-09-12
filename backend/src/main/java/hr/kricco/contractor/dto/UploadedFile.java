package hr.kricco.contractor.dto;

// An uploaded file as the service gets it. The controller copies it out of MultipartFile,
// so the services don't depend on Spring Web (services.md "Errors").
// originalName and contentType come from the client and can be anything.
public record UploadedFile(
        String originalName,
        String contentType,
        byte[] content
) {
}
