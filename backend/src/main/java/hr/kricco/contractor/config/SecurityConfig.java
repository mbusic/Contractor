package hr.kricco.contractor.config;

import hr.kricco.contractor.security.JwtAuthFilter;
import hr.kricco.contractor.security.JwtUtil;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Stateless: no session and no CSRF token, every call carries its JWT
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        // Photos: the document pages load them with <img>, which can't send a token.
                        // The names are random UUIDs, so nobody can guess one.
                        // TODO: replace public access to /api/files with short-lived signed URLs
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        // The frontend is hosted from Spring (static/), so its built files go through this filter chain too:
                        // index.html and its hashed files at the root. The browser loads them before anyone is logged in.
                        // "/" is forwarded to /index.html, and the forward is checked again, so both are listed.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/*.js", "/*.css").permitAll()
                        // Tomcat's internal forward to /error after an uncaught exception or sendError().
                        // The forwarded request has no authentication, so without this the real error would become a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Roles are checked per endpoint with @PreAuthorize
                        .anyRequest().authenticated())
                // Without this, a missing token gets 403. The API contract says 401.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtUtil, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
