package hr.kricco.contractor.exception;

// The action isn't allowed in the current state, e.g. deleting a branch that still has users.
// ApiExceptionHandler turns it into 409.
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
