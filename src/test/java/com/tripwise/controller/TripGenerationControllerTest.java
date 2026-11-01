package com.tripwise.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.entity.TripStatus;
import com.tripwise.exception.AiServiceException;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.repository.TripRepository;
import com.tripwise.testsupport.AiResponses;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end async generation: 202 + polling, persistence of the itinerary, failure handling and tenant isolation.
 * The HTTP call to the AI service is mocked; everything else (security, async executor, JPA, H2 schema) is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-generation-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=false"})
class TripGenerationControllerTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private TripItineraryRepository tripItineraryRepository;

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
    void generate_success_returns202ThenCompletedWithItinerary() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willReturn(aiResponse("45000.50"));
        long tripId = createTripId(aliceToken);

        generate(aliceToken, tripId)
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/trips/" + tripId + "/status")))
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.status").value("GENERATING"));

        awaitStatus(aliceToken, tripId, TripStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/trips/{id}", tripId).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.itinerary.version").value(1))
                .andExpect(jsonPath("$.itinerary.totalEstimatedCost").value(45000.50))
                .andExpect(jsonPath("$.itinerary.costBreakdown.items.length()").value(2))
                .andExpect(jsonPath("$.itinerary.costBreakdown.items[0].category").value("TRANSPORT"))
                .andExpect(jsonPath("$.itinerary.costBreakdown.items[0].mode").value("TRAIN"))
                .andExpect(jsonPath("$.itinerary.costBreakdown.totalExpected").value(45000.50))
                // 45000.50 > 90% of the 50000 budget, recomputed server-side
                .andExpect(jsonPath("$.itinerary.costBreakdown.budgetStatus").value("NEAR"))
                .andExpect(jsonPath("$.itinerary.plan.tripTitle").value("Goa Escape"))
                .andExpect(jsonPath("$.itinerary.plan.days[0].activities[0]").value("Beach"));

        verify(aiServiceClient).generateItinerary(any(AiGenerateTripRequest.class));
    }

    @Test
    void generate_sendsTripDataToAiService() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willReturn(aiResponse("1000"));
        long tripId = createTripId(aliceToken);

        generate(aliceToken, tripId).andExpect(status().isAccepted());
        awaitStatus(aliceToken, tripId, TripStatus.COMPLETED);

        org.mockito.ArgumentCaptor<AiGenerateTripRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerateTripRequest.class);
        verify(aiServiceClient).generateItinerary(captor.capture());
        AiGenerateTripRequest sent = captor.getValue();
        assertThat(sent.tripId()).isEqualTo(tripId);
        assertThat(sent.destination()).isEqualTo("Goa");
        assertThat(sent.origin()).isEqualTo("Mumbai");
        assertThat(sent.currency()).isEqualTo("INR");
        assertThat(sent.answers()).isEmpty();
        assertThat(sent.travellersCount()).isEqualTo(2);
        assertThat(sent.budgetAmount()).isEqualByComparingTo("50000.00");
        assertThat(sent.preferences()).containsExactly("beaches", "seafood");
    }

    @Test
    void generate_regeneration_incrementsItineraryVersion() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willReturn(aiResponse("1000"));
        long tripId = createTripId(aliceToken);

        generate(aliceToken, tripId).andExpect(status().isAccepted());
        awaitStatus(aliceToken, tripId, TripStatus.COMPLETED);
        generate(aliceToken, tripId).andExpect(status().isAccepted());
        await().atMost(WAIT).untilAsserted(() -> assertThat(
                tripItineraryRepository.findAll().stream()
                        .filter(i -> i.getTrip().getId() == tripId).findFirst().orElseThrow().getVersion())
                .isEqualTo(2));
        awaitStatus(aliceToken, tripId, TripStatus.COMPLETED);
    }

    @Test
    void generate_aiFailure_marksTripFailedAndStoresNoItinerary() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willThrow(new AiServiceException("AI service returned HTTP 500"));
        long tripId = createTripId(aliceToken);

        generate(aliceToken, tripId).andExpect(status().isAccepted());

        awaitStatus(aliceToken, tripId, TripStatus.FAILED);
        assertThat(tripItineraryRepository.findByTripIdAndTripUserId(tripId,
                tripRepository.findById(tripId).orElseThrow().getUser().getId())).isEmpty();
    }

    @Test
    void generate_unexpectedFailure_marksTripFailed() throws Exception {
        given(aiServiceClient.generateItinerary(any())).willThrow(new IllegalStateException("bug"));
        long tripId = createTripId(aliceToken);

        generate(aliceToken, tripId).andExpect(status().isAccepted());

        awaitStatus(aliceToken, tripId, TripStatus.FAILED);
    }

    @Test
    void generate_whileAlreadyGenerating_returns409() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        given(aiServiceClient.generateItinerary(any())).willAnswer(invocation -> {
            release.await(WAIT.toSeconds(), TimeUnit.SECONDS);
            return aiResponse("1000");
        });
        long tripId = createTripId(aliceToken);

        try {
            generate(aliceToken, tripId).andExpect(status().isAccepted());
            generate(aliceToken, tripId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Generation in progress"));
            statusOf(aliceToken, tripId).andExpect(jsonPath("$.status").value("GENERATING"));
        } finally {
            release.countDown();
        }
        awaitStatus(aliceToken, tripId, TripStatus.COMPLETED);
    }

    @Test
    void generate_otherUsersTrip_returns404AndDoesNotCallAi() throws Exception {
        long tripId = createTripId(aliceToken);

        generate(bobToken, tripId).andExpect(status().isNotFound());

        statusOf(aliceToken, tripId).andExpect(jsonPath("$.status").value("DRAFT"));
        verify(aiServiceClient, never()).generateItinerary(any());
    }

    @Test
    void generate_missingTrip_returns404() throws Exception {
        generate(aliceToken, 999_999L).andExpect(status().isNotFound());
    }

    @Test
    void generate_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/trips/{id}/generate", 1L)).andExpect(status().isUnauthorized());
    }

    @Test
    void status_newTrip_returnsDraft() throws Exception {
        long tripId = createTripId(aliceToken);

        statusOf(aliceToken, tripId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void status_otherUsersTrip_returns404() throws Exception {
        long tripId = createTripId(aliceToken);

        statusOf(bobToken, tripId).andExpect(status().isNotFound());
    }

    private void awaitStatus(String token, long tripId, TripStatus expected) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(50)).untilAsserted(() ->
                assertThat(json(statusOf(token, tripId)).get("status").asText()).isEqualTo(expected.name()));
    }

    private static AiItineraryResponse aiResponse(String cost) {
        return AiResponses.itinerary(cost);
    }

    private ResultActions generate(String token, long tripId) throws Exception {
        return mockMvc.perform(post("/api/v1/trips/{id}/generate", tripId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions statusOf(String token, long tripId) throws Exception {
        return mockMvc.perform(get("/api/v1/trips/{id}/status", tripId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long createTripId(String token) throws Exception {
        Map<String, Object> body = Map.of(
                "destination", "Goa",
                "origin", "Mumbai",
                "startDate", LocalDate.now().plusDays(10).toString(),
                "endDate", LocalDate.now().plusDays(14).toString(),
                "travellersCount", 2,
                "budgetAmount", new BigDecimal("50000.00"),
                "travelStyle", "RELAXED",
                "preferences", List.of("beaches", "seafood"));
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
