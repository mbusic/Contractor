package hr.kricco.contractor.exception;

// The user's role may call the endpoint, but this row isn't theirs, e.g. an order of another servicer.
// ApiExceptionHandler turns it into 403.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
