package hr.kricco.contractor.exception;

// The requested row doesn't exist. ApiExceptionHandler turns it into 404.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
