package com.tripwise.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rate-limit-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=true",
        "rate-limit.auth.capacity=3",
        "rate-limit.ai.capacity=1",
        "rate-limit.guest-generate.capacity=1"})
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    @Test
    void authEndpoints_overLimitPerIp_return429WithRetryAfter() throws Exception {
        String registered = register("10.0.0.1").andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readTree(registered).get("token").asText();
        login("10.0.0.1").andExpect(status().isUnauthorized());
        login("10.0.0.1").andExpect(status().isUnauthorized());

        login("10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));

        // Other clients and non-POST requests are unaffected
        login("10.0.0.2").andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token)
                        .with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }))
                .andExpect(status().isOk());
    }

    @Test
    void aiEndpoints_overLimitPerUser_return429() throws Exception {
        String body = register("10.0.0.50").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String token = "Bearer " + objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(post("/api/v1/trips/{id}/clarify", 999_999L).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/trips/{id}/generate", 999_999L).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isTooManyRequests());

        // Non-AI writes are not limited per user
        mockMvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
    }

    @Test
    void guestGenerate_overLimitPerIp_return429WithoutToken() throws Exception {
        guestGenerate("10.0.0.70").andExpect(status().isBadRequest());

        guestGenerate("10.0.0.70")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
        guestGenerate("10.0.0.71").andExpect(status().isBadRequest());
    }

    private ResultActions guestGenerate(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/guest/generate").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private ResultActions register(String ip) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "user-" + UUID.randomUUID() + "@example.com",
                "password", "Secret123!",
                "fullName", "Test User"));
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private ResultActions login(String ip) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", "nobody@example.com", "password", "Wrong123!"));
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }
}
