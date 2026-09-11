package hr.kricco.contractor.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

// No endpoint throws an unexpected exception on purpose, so the catch-all is tested directly.
// The other handlers are covered through the endpoint tests.
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("connection to db-host:5432 refused"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("Unexpected error");
    }
}
