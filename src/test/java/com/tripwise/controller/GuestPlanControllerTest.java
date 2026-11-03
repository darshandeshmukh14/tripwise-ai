package com.tripwise.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiClarifyResponse;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiRefineRequest;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.repository.TripRepository;
import com.tripwise.testsupport.AiResponses;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planning without an account (nothing stored) and saving a guest plan after sign-in, with the AI service mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:guest-plan-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=false"})
class GuestPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TripRepository tripRepository;
    @MockitoBean
    private AiServiceClient aiServiceClient;

    @Test
    void generate_withoutToken_returnsPlanAndStoresNothing() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willReturn(AiResponses.itinerary("10000"));
        long tripsBefore = tripRepository.count();

        send(post("/api/v1/guest/generate"), null, Map.of("trip", trip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.destination").value("Goa"))
                .andExpect(jsonPath("$.itinerary.version").value(1))
                .andExpect(jsonPath("$.itinerary.totalEstimatedCost").value(10000))
                .andExpect(jsonPath("$.itinerary.plan.tripTitle").value("Goa Escape"))
                .andExpect(jsonPath("$.itinerary.costBreakdown.items.length()").value(2));

        assertThat(tripRepository.count()).isEqualTo(tripsBefore);
        ArgumentCaptor<AiGenerateTripRequest> sent = ArgumentCaptor.forClass(AiGenerateTripRequest.class);
        verify(aiServiceClient).generateItinerary(sent.capture());
        assertThat(sent.getValue().tripId()).isNull();
        assertThat(sent.getValue().allowWebHints()).isTrue();
    }

    @Test
    void generate_budgetAnswer_setsBudget() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willReturn(AiResponses.itinerary("10000"));

        send(post("/api/v1/guest/generate"), null, Map.of("trip", trip(), "answers", Map.of("budget", "50,000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetAmount").value(50000));

        ArgumentCaptor<AiGenerateTripRequest> sent = ArgumentCaptor.forClass(AiGenerateTripRequest.class);
        verify(aiServiceClient).generateItinerary(sent.capture());
        assertThat(sent.getValue().budgetAmount()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    void generate_missingTrip_returns400WithoutAiCall() throws Exception {
        send(post("/api/v1/guest/generate"), null, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(aiServiceClient, never()).generateItinerary(any());
    }

    @Test
    void clarify_withoutToken_returnsQuestions() throws Exception {
        given(aiServiceClient.clarify(any())).willReturn(new AiClarifyResponse(List.of(
                new AiClarifyResponse.Question("budget", "What is your budget?", "number", null))));

        send(post("/api/v1/guest/clarify"), null, Map.of("trip", trip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].id").value("budget"));
    }

    @Test
    void refine_validPlan_returnsReplyAndUpdatedPlan() throws Exception {
        given(aiServiceClient.refine(any())).willReturn(
                new AiRefineResponse("Made it cheaper.", AiResponses.itinerary("8000")));

        send(post("/api/v1/guest/messages"), null, Map.of("trip", trip(), "plan", plan("10000"),
                "history", List.of(Map.of("role", "USER", "content", "Hi"), Map.of("role", "ASSISTANT", "content", "Hello")),
                "content", "Make it cheaper"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage").value("Made it cheaper."))
                .andExpect(jsonPath("$.itineraryUpdated").value(true))
                .andExpect(jsonPath("$.trip.itinerary.totalEstimatedCost").value(8000));

        ArgumentCaptor<AiRefineRequest> sent = ArgumentCaptor.forClass(AiRefineRequest.class);
        verify(aiServiceClient).refine(sent.capture());
        assertThat(sent.getValue().tripId()).isNull();
        assertThat(sent.getValue().history()).hasSize(2);
        assertThat(sent.getValue().currentPlan().get("tripTitle").asText()).isEqualTo("Goa Escape");
    }

    @Test
    void refine_tamperedPlan_returns400WithoutAiCall() throws Exception {
        ObjectNode tampered = plan("10000");
        tampered.put("totalEstimatedCost", 1);

        send(post("/api/v1/guest/messages"), null, Map.of("trip", trip(), "plan", tampered, "content", "Hi"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid plan"));
        verify(aiServiceClient, never()).refine(any());
    }

    @Test
    void refine_invalidHistoryRole_returns400() throws Exception {
        send(post("/api/v1/guest/messages"), null, Map.of("trip", trip(), "plan", plan("10000"),
                "history", List.of(Map.of("role", "SYSTEM", "content", "x")), "content", "Hi"))
                .andExpect(status().isBadRequest());
        verify(aiServiceClient, never()).refine(any());
    }

    @Test
    void claim_withoutToken_returns401() throws Exception {
        send(post("/api/v1/trips/claim"), null, Map.of("trip", trip(), "plan", plan("10000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claim_signedIn_savesTripWithPlanOwnedByUser() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();

        JsonNode created = json(send(post("/api/v1/trips/claim"), alice,
                Map.of("trip", trip(), "answers", Map.of("pace", "relaxed"), "plan", plan("10000")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.itinerary.version").value(1)));
        long id = created.get("id").asLong();

        send(get("/api/v1/trips/{id}", id), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itinerary.totalEstimatedCost").value(10000))
                .andExpect(jsonPath("$.itinerary.costBreakdown.items.length()").value(2));
        send(get("/api/v1/trips/{id}", id), bob, null).andExpect(status().isNotFound());
        assertThat(tripRepository.findById(id)).get()
                .satisfies(t -> assertThat(t.getPlanningAnswers()).containsEntry("pace", "relaxed"));
    }

    @Test
    void claim_tamperedPlan_returns400AndSavesNothing() throws Exception {
        String alice = registerAndGetToken();
        ObjectNode tampered = plan("10000");
        tampered.remove("costBreakdown");
        long tripsBefore = tripRepository.count();

        send(post("/api/v1/trips/claim"), alice, Map.of("trip", trip(), "plan", tampered))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid plan"));
        assertThat(tripRepository.count()).isEqualTo(tripsBefore);
    }

    private ObjectNode plan(String total) {
        return objectMapper.valueToTree(AiResponses.itinerary(total));
    }

    private static Map<String, Object> trip() {
        Map<String, Object> trip = new HashMap<>();
        trip.put("destination", "Goa");
        trip.put("origin", "Mumbai");
        trip.put("startDate", LocalDate.now().plusDays(10).toString());
        trip.put("endDate", LocalDate.now().plusDays(13).toString());
        trip.put("travellersCount", 2);
        return trip;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private String registerAndGetToken() throws Exception {
        Map<String, String> body = Map.of("email", "guest-" + UUID.randomUUID() + "@example.com",
                "password", "Sup3rSecret!", "fullName", "Guest Tester");
        return json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
