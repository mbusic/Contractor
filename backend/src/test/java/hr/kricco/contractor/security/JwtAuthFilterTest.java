package hr.kricco.contractor.security;

import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static com.jayway.jsonpath.JsonPath.read;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Calls a protected endpoint (GET /api/branches) with different tokens.
// Runs against contractor_test. Each test is rolled back, so tests don't see each other's rows.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JwtAuthFilterTest {

    private static final String PROTECTED_URL = "/api/branches";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.jwt.secret}")
    private String secret;

    @Test
    void tokenFromLoginOpensProtectedEndpoint() throws Exception {
        saveAdmin("admin", "secret");
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "password": "secret"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = read(loginResponse, "$.token");

        callWithToken(token).andExpect(status().isOk());
    }

    // Endpoint tests use @WithMockUser, which skips UserPrincipal.
    // This checks that the user's role from the database reaches @PreAuthorize as ROLE_SERVICER.
    @Test
    void servicerTokenCanReadButNotWriteBranches() throws Exception {
        User servicer = saveUser("servicer", "secret", Role.SERVICER);
        String token = jwtUtil.generate(servicer);

        callWithToken(token).andExpect(status().isOk());
        mockMvc.perform(post(PROTECTED_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Kricco Rijeka"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithGarbageTokenReturnsUnauthorized() throws Exception {
        callWithToken("not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithTokenSignedByAnotherKeyReturnsUnauthorized() throws Exception {
        saveAdmin("admin", "secret");
        String token = Jwts.builder()
                .subject("admin")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor("some-other-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();

        callWithToken(token).andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithExpiredTokenReturnsUnauthorized() throws Exception {
        saveAdmin("admin", "secret");
        String token = Jwts.builder()
                .subject("admin")
                .expiration(Date.from(Instant.now().minus(Duration.ofMinutes(1))))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        callWithToken(token).andExpect(status().isUnauthorized());
    }

    @Test
    void requestFromDeletedUserReturnsUnauthorized() throws Exception {
        User admin = saveAdmin("admin", "secret");
        String token = jwtUtil.generate(admin);
        userRepository.delete(admin);
        userRepository.flush();

        callWithToken(token).andExpect(status().isUnauthorized());
    }

    private ResultActions callWithToken(String token) throws Exception {
        return mockMvc.perform(get(PROTECTED_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private User saveAdmin(String username, String password) {
        return saveUser(username, password, Role.ADMIN);
    }

    private User saveUser(String username, String password, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setDisplayName("Test " + username);
        return userRepository.save(user);
    }
}
