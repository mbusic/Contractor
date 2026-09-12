package hr.kricco.contractor.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

// No endpoint throws an unexpected exception on purpose, so the catch-all is tested directly.
// The same for the @Version failure: it needs two transactions saving the same row at the same time.
// The other handlers are covered through the endpoint tests.
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("connection to db-host:5432 refused"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("Unexpected error");
    }

    @Test
    void optimisticLockFailureReturns409() {
        ProblemDetail problem = handler.handleOptimisticLock(new OptimisticLockingFailureException("Row was updated"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Changed by someone else. Reload and try again.");
    }
}
