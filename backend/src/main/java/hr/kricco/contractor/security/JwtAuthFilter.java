package hr.kricco.contractor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Reads "Authorization: Bearer <token>" and puts the token's user into the security context.
// A missing or invalid token leaves the request unauthenticated, so protected endpoints answer 401.
// Not a @Component: SecurityConfig creates it, so Spring Boot doesn't also register it as a plain servlet filter.
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtUtil.getValidUsername(token).ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String username) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (UsernameNotFoundException e) {
            // The user was deleted or deactivated after the token was issued - the request stays unauthenticated
        }
    }
}
