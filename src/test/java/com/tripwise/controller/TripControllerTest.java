package com.tripwise.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStatus;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end trip CRUD and tenant isolation through security, MVC, JPA and the H2 Flyway schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "rate-limit.enabled=false"})
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private TripItineraryRepository tripItineraryRepository;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        aliceToken = registerAndGetToken();
        bobToken = registerAndGetToken();
    }

    @Test
    void createTrip_validRequest_returns201DraftWithLocation() throws Exception {
        createTrip(aliceToken, validTrip("Goa"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/v1/trips/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.destination").value("Goa"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.budgetAmount").value(50000.00))
                .andExpect(jsonPath("$.preferences[0]").value("beaches"))
                .andExpect(jsonPath("$.origin").value("Mumbai"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.comfortLevel").value("STANDARD"))
                .andExpect(jsonPath("$.transportModes[0]").value("TRAIN"))
                .andExpect(jsonPath("$.itinerary").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void createTrip_invalidRequest_returns400WithFieldErrors() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("destination", " ");
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusDays(5).toString());
        body.put("travellersCount", 0);
        body.put("budgetAmount", -1);

        createTrip(aliceToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.destination").exists())
                .andExpect(jsonPath("$.errors.origin").exists())
                .andExpect(jsonPath("$.errors.travellersCount").exists())
                .andExpect(jsonPath("$.errors.budgetAmount").exists())
                .andExpect(jsonPath("$.errors.endDateOnOrAfterStartDate").value("endDate must be on or after startDate"));
    }

    @Test
    void createTrip_userIdInBody_isIgnored() throws Exception {
        long bobTripCount = countTrips(bobToken);
        Map<String, Object> body = validTrip("Paris");
        body.put("userId", 999_999);

        createTrip(bobToken, body).andExpect(status().isCreated());

        assertThat(countTrips(bobToken)).isEqualTo(bobTripCount + 1);
    }

    @Test
    void createTrip_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTrip("Goa"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserTrips_returnsOnlyOwnTripsPaginatedNewestFirst() throws Exception {
        createTripId(aliceToken, "Goa");
        createTripId(aliceToken, "Jaipur");
        createTripId(aliceToken, "Kerala");
        createTripId(bobToken, "Paris");

        mockMvc.perform(get("/api/v1/trips").param("page", "0").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].destination").value("Kerala"))
                .andExpect(jsonPath("$.content[1].destination").value("Jaipur"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].destination").value("Paris"));
    }

    @Test
    void getUserTrips_unknownSortProperty_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/trips").param("sort", "doesNotExist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTripById_owner_returns200() throws Exception {
        long id = createTripId(aliceToken, "Goa");

        mockMvc.perform(get("/api/v1/trips/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.travellersCount").value(2))
                .andExpect(jsonPath("$.itinerary").doesNotExist());
    }

    @Test
    void getTripById_otherUsersTrip_returns404() throws Exception {
        long aliceTripId = createTripId(aliceToken, "Goa");

        mockMvc.perform(get("/api/v1/trips/{id}", aliceTripId).header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Trip not found."));
    }

    @Test
    void getTripById_missing_returnsSame404() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trip not found."));
    }

    @Test
    void getTripById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/trips/abc").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTripById_withItinerary_returnsParsedPlanJson() throws Exception {
        long id = createTripId(aliceToken, "Goa");
        Trip trip = tripRepository.findById(id).orElseThrow();
        TripItinerary itinerary = new TripItinerary();
        itinerary.setTrip(trip);
        itinerary.setTotalEstimatedCost(new BigDecimal("42000.50"));
        itinerary.setPlanData(objectMapper.readTree("{\"days\":[{\"day\":1,\"activities\":[\"Beach\"]}]}"));
        tripItineraryRepository.save(itinerary);

        mockMvc.perform(get("/api/v1/trips/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itinerary.version").value(1))
                .andExpect(jsonPath("$.itinerary.totalEstimatedCost").value(42000.50))
                .andExpect(jsonPath("$.itinerary.plan.days[0].day").value(1))
                .andExpect(jsonPath("$.itinerary.plan.days[0].activities[0]").value("Beach"));
    }

    @Test
    void createTrip_unknownTransportMode_returns400() throws Exception {
        Map<String, Object> body = validTrip("Goa");
        body.put("transportModes", List.of("TELEPORT"));

        createTrip(aliceToken, body).andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_tooLong_returns400() throws Exception {
        Map<String, Object> body = validTrip("Goa");
        body.put("endDate", LocalDate.now().plusDays(10 + 60).toString());

        createTrip(aliceToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.durationWithinLimit").exists());
    }

    @Test
    void updateTrip_owner_replacesFieldsAndResetsToDraft() throws Exception {
        long id = createTripId(aliceToken, "Goa");
        setStatus(id, TripStatus.FAILED);
        Map<String, Object> body = validTrip("Manali");
        body.put("origin", "Delhi");
        body.put("comfortLevel", "LUXURY");
        body.put("transportModes", List.of("CAR", "BUS"));

        updateTrip(aliceToken, id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Manali"))
                .andExpect(jsonPath("$.origin").value("Delhi"))
                .andExpect(jsonPath("$.comfortLevel").value("LUXURY"))
                .andExpect(jsonPath("$.transportModes.length()").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void updateTrip_otherUsersTrip_returns404AndLeavesTripUnchanged() throws Exception {
        long id = createTripId(aliceToken, "Goa");

        updateTrip(bobToken, id, validTrip("Hacked")).andExpect(status().isNotFound());

        assertThat(tripRepository.findById(id).orElseThrow().getDestination()).isEqualTo("Goa");
    }

    @Test
    void updateTrip_whileGenerating_returns409() throws Exception {
        long id = createTripId(aliceToken, "Goa");
        setStatus(id, TripStatus.GENERATING);

        updateTrip(aliceToken, id, validTrip("Manali"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Generation in progress"));
    }

    @Test
    void updateTrip_invalidBody_returns400() throws Exception {
        long id = createTripId(aliceToken, "Goa");
        Map<String, Object> body = validTrip("Goa");
        body.remove("origin");

        updateTrip(aliceToken, id, body).andExpect(status().isBadRequest());
    }

    @Test
    void deleteTrip_owner_returns204AndCascadesItinerary() throws Exception {
        long id = createTripId(aliceToken, "Goa");
        TripItinerary itinerary = new TripItinerary();
        itinerary.setTrip(tripRepository.findById(id).orElseThrow());
        itinerary.setPlanData(objectMapper.readTree("{\"days\":[]}"));
        tripItineraryRepository.save(itinerary);

        mockMvc.perform(delete("/api/v1/trips/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNoContent());

        assertThat(tripRepository.findById(id)).isEmpty();
        assertThat(tripItineraryRepository.findAll()).noneMatch(i -> i.getTrip().getId() == id);
    }

    @Test
    void deleteTrip_otherUsersTrip_returns404() throws Exception {
        long id = createTripId(aliceToken, "Goa");

        mockMvc.perform(delete("/api/v1/trips/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());

        assertThat(tripRepository.findById(id)).isPresent();
    }

    @Test
    void deleteTrip_whileGenerating_returns409() throws Exception {
        long id = createTripId(aliceToken, "Goa");
        setStatus(id, TripStatus.GENERATING);

        mockMvc.perform(delete("/api/v1/trips/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isConflict());
    }

    private void setStatus(long tripId, TripStatus status) {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        trip.setStatus(status);
        tripRepository.save(trip);
    }

    private ResultActions updateTrip(String token, long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/trips/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> validTrip(String destination) {
        Map<String, Object> body = new HashMap<>();
        body.put("destination", destination);
        body.put("origin", "Mumbai");
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusDays(14).toString());
        body.put("travellersCount", 2);
        body.put("budgetAmount", new BigDecimal("50000.00"));
        body.put("travelStyle", "RELAXED");
        body.put("transportModes", List.of("TRAIN"));
        body.put("preferences", List.of("beaches", "seafood"));
        return body;
    }

    private ResultActions createTrip(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/trips")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long createTripId(String token, String destination) throws Exception {
        return json(createTrip(token, validTrip(destination)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long countTrips(String token) throws Exception {
        return json(mockMvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, bearer(token))))
                .get("totalElements").asLong();
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
