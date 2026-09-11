package hr.kricco.contractor.exception;

// The request is valid JSON but breaks a business rule, e.g. a servicer without a branch
// or an unknown ID in the body. ApiExceptionHandler turns it into 400.
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
