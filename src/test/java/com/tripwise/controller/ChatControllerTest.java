package com.tripwise.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiRefineRequest;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.exception.AiServiceException;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.testsupport.AiResponses;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat refinement end-to-end (security, transactions, H2 schema); only the AI HTTP client is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=false"})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiServiceClient aiServiceClient;
    @Autowired
    private TripItineraryRepository tripItineraryRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        aliceToken = registerAndGetToken();
        bobToken = registerAndGetToken();
        given(aiServiceClient.generateItinerary(any())).willReturn(AiResponses.itinerary("40000"));
    }

    @Test
    void sendMessage_beforeItinerary_returns409() throws Exception {
        long tripId = createTripId(aliceToken);

        send(aliceToken, tripId, "Make it cheaper")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Itinerary not ready"));
        verify(aiServiceClient, never()).refine(any());
    }

    @Test
    void sendMessage_replyOnly_persistsBothMessagesAndKeepsVersion() throws Exception {
        long tripId = generatedTripId(aliceToken);
        given(aiServiceClient.refine(any())).willReturn(new AiRefineResponse("Day 2 has a beach afternoon.", null));

        send(aliceToken, tripId, "  What is on day 2?  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.senderRole").value("USER"))
                .andExpect(jsonPath("$.userMessage.content").value("What is on day 2?"))
                .andExpect(jsonPath("$.assistantMessage.senderRole").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.content").value("Day 2 has a beach afternoon."))
                .andExpect(jsonPath("$.itineraryUpdated").value(false))
                .andExpect(jsonPath("$.itineraryVersion").value(1));

        messages(aliceToken, tripId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].senderRole").value("ASSISTANT"))
                .andExpect(jsonPath("$.content[1].senderRole").value("USER"));
    }

    @Test
    void sendMessage_withUpdatedPlan_storesNewVersionAndCostBreakdown() throws Exception {
        long tripId = generatedTripId(aliceToken);
        given(aiServiceClient.refine(any()))
                .willReturn(new AiRefineResponse("Switched to a cheaper hotel.", AiResponses.itinerary("30000")));

        send(aliceToken, tripId, "Cheaper hotel please")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itineraryUpdated").value(true))
                .andExpect(jsonPath("$.itineraryVersion").value(2));

        mockMvc.perform(get("/api/v1/trips/{id}", tripId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.itinerary.version").value(2))
                .andExpect(jsonPath("$.itinerary.totalEstimatedCost").value(30000.00))
                .andExpect(jsonPath("$.itinerary.costBreakdown.totalExpected").value(30000.00))
                .andExpect(jsonPath("$.itinerary.costBreakdown.items.length()").value(2))
                .andExpect(jsonPath("$.itinerary.costBreakdown.budgetStatus").value("WITHIN"));
    }

    @Test
    void sendMessage_sendsPreviousTurnsAsHistory() throws Exception {
        long tripId = generatedTripId(aliceToken);
        given(aiServiceClient.refine(any())).willReturn(new AiRefineResponse("Answer", null));

        send(aliceToken, tripId, "First").andExpect(status().isOk());
        send(aliceToken, tripId, "Second").andExpect(status().isOk());

        ArgumentCaptor<AiRefineRequest> captor = ArgumentCaptor.forClass(AiRefineRequest.class);
        verify(aiServiceClient, times(2)).refine(captor.capture());
        AiRefineRequest second = captor.getAllValues().get(1);
        assertThat(second.message()).isEqualTo("Second");
        assertThat(second.history()).extracting(AiRefineRequest.Turn::role).containsExactly("USER", "ASSISTANT");
        assertThat(second.history()).extracting(AiRefineRequest.Turn::content).containsExactly("First", "Answer");
        assertThat(second.currentPlan().get("tripTitle").asText()).isEqualTo("Goa Escape");
        assertThat(second.trip().origin()).isEqualTo("Mumbai");
    }

    @Test
    void sendMessage_aiFailure_returns503AndPersistsNothing() throws Exception {
        long tripId = generatedTripId(aliceToken);
        given(aiServiceClient.refine(any())).willThrow(new AiServiceException("AI service returned HTTP 500"));

        send(aliceToken, tripId, "Hello")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The AI planner is temporarily unavailable. Please try again later."));

        messages(aliceToken, tripId).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void chat_otherUsersTrip_returns404AndDoesNotCallAi() throws Exception {
        long tripId = generatedTripId(aliceToken);

        send(bobToken, tripId, "Hi").andExpect(status().isNotFound());
        messages(bobToken, tripId).andExpect(status().isNotFound());
        verify(aiServiceClient, never()).refine(any());
    }

    @Test
    void sendMessage_blankContent_returns400() throws Exception {
        long tripId = createTripId(aliceToken);

        send(aliceToken, tripId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void sendMessage_staleExpectedVersion_returns409WithoutCallingAi() throws Exception {
        long tripId = generatedTripId(aliceToken);

        send(aliceToken, tripId, Map.of("content", "Cheaper please", "expectedVersion", 7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Itinerary changed"));
        verify(aiServiceClient, never()).refine(any());
    }

    @Test
    void sendMessage_itineraryChangedWhileAiAnswers_returns409AndPersistsNothing() throws Exception {
        long tripId = generatedTripId(aliceToken);
        given(aiServiceClient.refine(any())).willAnswer(invocation -> {
            // Another request refines the plan while the AI is still answering this one
            transactionTemplate.executeWithoutResult(status -> tripItineraryRepository.findAll().stream()
                    .filter(itinerary -> itinerary.getTrip().getId() == tripId)
                    .forEach(itinerary -> itinerary.setVersion(itinerary.getVersion() + 1)));
            return new AiRefineResponse("Switched hotel.", AiResponses.itinerary("30000"));
        });

        send(aliceToken, tripId, Map.of("content", "Cheaper hotel", "expectedVersion", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Itinerary changed"));

        messages(aliceToken, tripId).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/trips/{id}", tripId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(jsonPath("$.itinerary.version").value(2))
                .andExpect(jsonPath("$.itinerary.totalEstimatedCost").value(40000.00));
    }

    @Test
    void sendMessage_reusesAnswersGivenAtGeneration() throws Exception {
        long tripId = createTripId(aliceToken);
        mockMvc.perform(post("/api/v1/trips/{id}/generate", tripId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", Map.of("pace", "relaxed")))))
                .andExpect(status().isAccepted());
        awaitCompleted(aliceToken, tripId);
        given(aiServiceClient.refine(any())).willReturn(new AiRefineResponse("Sure.", null));

        send(aliceToken, tripId, "Any tips?").andExpect(status().isOk());

        ArgumentCaptor<AiRefineRequest> captor = ArgumentCaptor.forClass(AiRefineRequest.class);
        verify(aiServiceClient).refine(captor.capture());
        assertThat(captor.getValue().trip().answers()).containsEntry("pace", "relaxed");
    }

    private long generatedTripId(String token) throws Exception {
        long tripId = createTripId(token);
        mockMvc.perform(post("/api/v1/trips/{id}/generate", tripId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isAccepted());
        awaitCompleted(token, tripId);
        return tripId;
    }

    private void awaitCompleted(String token, long tripId) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).untilAsserted(() ->
                assertThat(json(mockMvc.perform(get("/api/v1/trips/{id}/status", tripId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))).get("status").asText())
                        .isEqualTo("COMPLETED"));
    }

    private ResultActions send(String token, long tripId, String content) throws Exception {
        return send(token, tripId, Map.of("content", content));
    }

    private ResultActions send(String token, long tripId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/trips/{id}/messages", tripId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions messages(String token, long tripId) throws Exception {
        return mockMvc.perform(get("/api/v1/trips/{id}/messages", tripId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long createTripId(String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("destination", "Goa");
        body.put("origin", "Mumbai");
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusDays(14).toString());
        body.put("travellersCount", 2);
        body.put("budgetAmount", new BigDecimal("50000.00"));
        body.put("preferences", List.of("beaches"));
        return json(mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private String registerAndGetToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "user-" + UUID.randomUUID() + "@example.com",
                "password", "Secret123!",
                "fullName", "Test User"));
        return json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()))
                .get("token").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
