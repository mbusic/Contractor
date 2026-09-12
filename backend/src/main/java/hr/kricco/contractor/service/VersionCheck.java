package hr.kricco.contractor.service;

import hr.kricco.contractor.exception.BadRequestException;
import hr.kricco.contractor.exception.ConflictException;

// Optimistic locking for writes from the UI: the request sends the version it read.
// If someone saved in between, the versions differ and the write fails instead of overwriting.
// A save that happens between this check and the flush is caught by @Version (see ApiExceptionHandler).
// Updates use saveAndFlush: Hibernate increases the version only on flush, and the response must show the new one.
public final class VersionCheck {

    // Also used by ApiExceptionHandler for the @Version failure, so both cases look the same to the client
    public static final String CHANGED_MESSAGE = "Changed by someone else. Reload and try again.";

    private VersionCheck() {
    }

    static void check(Long requestVersion, Long currentVersion) {
        if (requestVersion == null) {
            throw new BadRequestException("Version is required");
        }
        if (!requestVersion.equals(currentVersion)) {
            throw new ConflictException(CHANGED_MESSAGE);
        }
    }
}
