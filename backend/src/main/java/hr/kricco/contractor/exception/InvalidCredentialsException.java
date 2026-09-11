package hr.kricco.contractor.exception;

// Login failed. The same message for an unknown username and a wrong password.
// ApiExceptionHandler turns it into 401.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
