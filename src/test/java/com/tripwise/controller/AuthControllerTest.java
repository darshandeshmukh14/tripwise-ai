package com.tripwise.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.entity.User;
import com.tripwise.repository.UserRepository;
import com.tripwise.security.AuthenticatedUser;
import com.tripwise.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow through the real security filter chain, Flyway (H2 mirror) and JPA validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=false",
        "jwt.refresh-reuse-grace=0s",
        "cors.allowed-origins=http://localhost:3000"})
class AuthControllerTest {

    private static final String PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    void register_validRequest_returns201WithTokenAndStoresHashedPassword() throws Exception {
        String email = uniqueEmail();

        JsonNode body = json(register(email, PASSWORD, "Alice Smith")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(jwtService.parseToken(body.get("token").asText()))
                .contains(new AuthenticatedUser(user.getId(), email));
    }

    @Test
    void register_duplicateEmailDifferentCase_returns409() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD, "Alice")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
    }

    @Test
    void register_invalidBody_returns400WithFieldErrors() throws Exception {
        register("not-an-email", "short", " ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(content().string(not(containsString("short\""))));
    }

    @Test
    void register_malformedJson_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());

        login(email.toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Alice"))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());

        login(email, "WrongPass1!")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void login_unknownEmail_returnsSame401AsWrongPassword() throws Exception {
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/trips"));
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_passesSecurity() throws Exception {
        String email = uniqueEmail();
        String token = json(register(email, PASSWORD, "Alice")).get("token").asText();

        mockMvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void me_withToken_returnsCurrentUser() throws Exception {
        String email = uniqueEmail();
        JsonNode registered = json(register(email, PASSWORD, "Alice"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(registered.get("userId").asLong()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Alice"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validToken_rotatesAndReturnsNewTokens() throws Exception {
        String email = uniqueEmail();
        JsonNode registered = json(register(email, PASSWORD, "Alice"));
        String refreshToken = registered.get("refreshToken").asText();
        assertThat(registered.get("refreshTokenExpiresAt").isTextual()).isTrue();

        JsonNode refreshed = json(refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").isString()));

        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(refreshToken);
        assertThat(jwtService.parseToken(refreshed.get("token").asText())).isPresent();
    }

    @Test
    void refresh_reusedToken_returns401AndRevokesWholeSession() throws Exception {
        String original = json(register(uniqueEmail(), PASSWORD, "Alice")).get("refreshToken").asText();
        String rotated = json(refresh(original).andExpect(status().isOk())).get("refreshToken").asText();

        refresh(original)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        refresh(rotated).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        String refreshToken = json(register(uniqueEmail(), PASSWORD, "Alice")).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_unknownOrBlankToken_isRejected() throws Exception {
        refresh("does-not-exist").andExpect(status().isUnauthorized());
        refresh(" ").andExpect(status().isBadRequest());
    }

    @Test
    void cors_preflightFromAllowedOrigin_isAccepted() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void cors_preflightFromUnknownOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private ResultActions register(String email, String password, String fullName) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password, "fullName", fullName));
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
