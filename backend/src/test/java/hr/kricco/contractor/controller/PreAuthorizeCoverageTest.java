package hr.kricco.contractor.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Every endpoint must check the role with @PreAuthorize on its method (specs/rest-api.md [A1]).
// Without it, any logged-in user could call the endpoint, a CLIENT user included.
@SpringBootTest
class PreAuthorizeCoverageTest {

    // Public endpoints, opened in SecurityConfig
    private static final Set<Class<?>> PUBLIC_CONTROLLERS = Set.of(AuthController.class, HealthController.class);

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyEndpointHasPreAuthorize() {
        List<HandlerMethod> endpoints = handlerMapping.getHandlerMethods().values().stream()
                .filter(this::isOurController)
                .filter(handler -> !PUBLIC_CONTROLLERS.contains(handler.getBeanType()))
                .toList();

        List<String> withoutPreAuthorize = endpoints.stream()
                .filter(handler -> !handler.hasMethodAnnotation(PreAuthorize.class))
                .map(HandlerMethod::toString)
                .toList();

        assertThat(endpoints).isNotEmpty();
        assertThat(withoutPreAuthorize).isEmpty();
    }

    // Skips Spring Boot's own controllers, e.g. BasicErrorController
    private boolean isOurController(HandlerMethod handler) {
        return handler.getBeanType().getPackageName().startsWith("hr.kricco.contractor");
    }
}
