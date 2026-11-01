package com.tripwise.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiItineraryResponse.AmountRange;
import com.tripwise.client.dto.AiRouteRequest;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiSuggestRequest;
import com.tripwise.client.dto.AiSuggestResponse;
import com.tripwise.entity.Season;
import com.tripwise.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multi-city stops and currency through MVC, JPA and the H2 Flyway schema, with the AI service mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-stops-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=false"})
class TripStopsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AiServiceClient aiServiceClient;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        aliceToken = registerAndGetToken();
        bobToken = registerAndGetToken();
    }

    @Test
    void createTrip_withStops_persistsCanonicalRouteAndCurrency() throws Exception {
        givenRoute(true, stop(1, "Delhi", 2), stop(2, "Agra", 1));

        send(post("/api/v1/trips"), aliceToken, multiCity("eur"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destination").value("Delhi"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.stops.length()").value(2))
                .andExpect(jsonPath("$.stops[0].seq").value(1))
                .andExpect(jsonPath("$.stops[1].city").value("Agra"))
                .andExpect(jsonPath("$.stops[1].countryCode").value("IN"));
    }

    @Test
    void updateTrip_replacesStopsWithoutUniqueViolation() throws Exception {
        givenRoute(true, stop(1, "Delhi", 2), stop(2, "Agra", 1));
        long id = json(send(post("/api/v1/trips"), aliceToken, multiCity(null))).get("id").asLong();
        givenRoute(true, stop(1, "Agra", 1), stop(2, "Delhi", 2));

        send(put("/api/v1/trips/{id}", id), aliceToken, multiCity(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].city").value("Agra"))
                .andExpect(jsonPath("$.stops[1].city").value("Delhi"))
                .andExpect(jsonPath("$.destination").value("Agra"));
    }

    @Test
    void createTrip_stopsTooFar_returns422WithViolations() throws Exception {
        given(aiServiceClient.checkRoute(any(AiRouteRequest.class))).willReturn(new AiRouteResponse(
                List.of(stop(1, "Delhi", 2), stop(2, "Agra", 1)), List.of(), false,
                List.of("Delhi to Agra is 900 km by road (max 400 km)")));

        send(post("/api/v1/trips"), aliceToken, multiCity(null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Stops too far apart"))
                .andExpect(jsonPath("$.violations[0]").value("Delhi to Agra is 900 km by road (max 400 km)"));
    }

    @Test
    void createTrip_aiDownWithStops_returns503() throws Exception {
        given(aiServiceClient.checkRoute(any(AiRouteRequest.class))).willThrow(new AiServiceException("down"));

        send(post("/api/v1/trips"), aliceToken, multiCity(null)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void createTrip_nightsDoNotMatchDates_returns400WithoutAiCall() throws Exception {
        Map<String, Object> body = multiCity(null);
        body.put("stops", List.of(Map.of("city", "Delhi", "nights", 1), Map.of("city", "Agra", "nights", 1)));

        send(post("/api/v1/trips"), aliceToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stopsConsistent").exists());
        verify(aiServiceClient, never()).checkRoute(any());
    }

    @Test
    void createTrip_sevenStops_returns400() throws Exception {
        Map<String, Object> body = multiCity(null);
        body.put("stops", Collections.nCopies(7, Map.of("city", "Delhi", "nights", 1)));

        send(post("/api/v1/trips"), aliceToken, body).andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_unsupportedCurrency_returns400() throws Exception {
        Map<String, Object> body = multiCity("XYZ");
        body.remove("stops");

        send(post("/api/v1/trips"), aliceToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currencySupported").exists());
    }

    @Test
    void suggestStops_ownTrip_returnsSuggestions() throws Exception {
        givenRoute(true, stop(1, "Delhi", 2), stop(2, "Agra", 1));
        long id = json(send(post("/api/v1/trips"), aliceToken, multiCity(null))).get("id").asLong();
        given(aiServiceClient.suggestStops(any(AiSuggestRequest.class))).willReturn(new AiSuggestResponse(List.of(
                new AiSuggestResponse.Suggestion("Mathura", "IN", "Agra", new BigDecimal("58"), new BigDecimal("1.5"),
                        1, new AmountRange(new BigDecimal("3000"), new BigDecimal("4000"), new BigDecimal("5000")),
                        "INR", Season.SHOULDER, "Krishna temples"))));

        send(post("/api/v1/trips/{id}/stops/suggest", id), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].city").value("Mathura"))
                .andExpect(jsonPath("$.suggestions[0].extraCostExpected").value(4000));
    }

    @Test
    void suggestStops_otherUsersTrip_returns404WithoutAiCall() throws Exception {
        givenRoute(true, stop(1, "Delhi", 2), stop(2, "Agra", 1));
        long id = json(send(post("/api/v1/trips"), aliceToken, multiCity(null))).get("id").asLong();

        send(post("/api/v1/trips/{id}/stops/suggest", id), bobToken, null).andExpect(status().isNotFound());
        verify(aiServiceClient, never()).suggestStops(any());
    }

    private void givenRoute(boolean valid, AiRouteResponse.Stop... stops) {
        given(aiServiceClient.checkRoute(any(AiRouteRequest.class)))
                .willReturn(new AiRouteResponse(List.of(stops), List.of(), valid, List.of()));
    }

    private static AiRouteResponse.Stop stop(int seq, String city, int nights) {
        return new AiRouteResponse.Stop(seq, city, "IN", nights, true);
    }

    private static Map<String, Object> multiCity(String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("destination", "delhi");
        body.put("origin", "Mumbai");
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusDays(13).toString());
        body.put("travellersCount", 2);
        if (currency != null) {
            body.put("currency", currency);
        }
        body.put("stops", List.of(Map.of("city", "delhi", "nights", 2), Map.of("city", "agra", "nights", 1)));
        return body;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private String registerAndGetToken() throws Exception {
        Map<String, String> body = Map.of("email", "stops-" + UUID.randomUUID() + "@example.com",
                "password", "Sup3rSecret!", "fullName", "Stops Tester");
        ResultActions result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        return json(result).get("token").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
