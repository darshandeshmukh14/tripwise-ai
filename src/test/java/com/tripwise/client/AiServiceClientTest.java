package com.tripwise.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tripwise.client.dto.AiClarifyResponse;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiRefineRequest;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.client.dto.AiRouteRequest;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiStop;
import com.tripwise.client.dto.AiSuggestRequest;
import com.tripwise.client.dto.AiSuggestResponse;
import com.tripwise.config.AiServiceClientConfig;
import com.tripwise.config.AiServiceProperties;
import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.Season;
import com.tripwise.entity.TransportMode;
import com.tripwise.exception.AiServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real RestClient configuration (headers, timeouts, error mapping) against a local HTTP server.
 */
class AiServiceClientTest {

    private static final String TOKEN = "test-internal-token";
    private static final String VALID_BODY = """
            {"tripTitle":"Goa Escape","destination":"Goa","origin":"Mumbai","comfortLevel":"STANDARD",
             "totalEstimatedCostInr":45000.55,"totalCost":{"min":40000,"expected":45000.55,"max":52000},
             "budgetStatus":"WITHIN","season":{"type":"PEAK","multiplier":1.35,"notes":"Winter peak"},
             "transportOptions":[{"mode":"TRAIN","travelClass":"3A","distanceKm":590,"durationHours":11.5,
               "cost":{"min":3600,"expected":4000,"max":4400},"comfortScore":3,"recommended":true,"notes":null}],
             "stay":{"tier":"STANDARD","nightlyRate":{"min":3000,"expected":3500,"max":4000},"nights":4,"rooms":1,
               "areaSuggestion":"Calangute"},
             "costBreakdown":[
               {"category":"TRANSPORT","mode":"TRAIN","label":"Mumbai to Goa train","amount":{"min":3600,"expected":4000,"max":4400},
                "season":"PEAK","source":"ESTIMATE","confidence":"HIGH","assumptions":"3A, 2 travellers"},
               {"category":"STAY","mode":null,"label":"Hotel 4 nights","amount":{"min":36000,"expected":41000.55,"max":47600},
                "season":"PEAK","source":"ESTIMATE","confidence":"MEDIUM","assumptions":null}],
             "warnings":[],"days":[{"day":1,"activities":["Beach"]}],"extra":"ignored"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();

    private volatile int responseStatus = 200;
    private volatile String responseBody = VALID_BODY;
    private volatile long responseDelayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sleep(responseDelayMs);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException ignored) {
                // client gave up (timeout test)
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void generateItinerary_success_sendsTokenAndBodyAndParsesResponse() throws Exception {
        AiItineraryResponse response = client(Duration.ofSeconds(5)).generateItinerary(request());

        assertThat(receivedPath.get()).isEqualTo("/internal/v1/itineraries/generate");
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        JsonNode sent = objectMapper.readTree(receivedBody.get());
        assertThat(sent.get("tripId").asLong()).isEqualTo(42L);
        assertThat(sent.get("startDate").asText()).isEqualTo("2030-01-10");
        assertThat(sent.get("preferences").get(0).asText()).isEqualTo("beaches");

        assertThat(response.tripTitle()).isEqualTo("Goa Escape");
        assertThat(response.totalEstimatedCostInr()).isEqualByComparingTo("45000.55");
        assertThat(response.season().type()).isEqualTo(Season.PEAK);
        assertThat(response.costBreakdown()).hasSize(2);
        assertThat(response.transportOptions().get(0).mode()).isEqualTo(TransportMode.TRAIN);
        assertThat(response.days().isArray()).isTrue();
        assertThat(sent.get("origin").asText()).isEqualTo("Mumbai");
        assertThat(sent.get("comfortLevel").asText()).isEqualTo("PREMIUM");
        assertThat(sent.get("transportModes").get(0).asText()).isEqualTo("TRAIN");
        assertThat(sent.get("answers").get("pace").asText()).isEqualTo("relaxed");
    }

    @Test
    void generateItinerary_serverError_throwsAiServiceException() {
        responseStatus = 500;
        responseBody = "{\"detail\":\"boom\"}";

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generateItinerary(request()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("500");
    }

    @Test
    void generateItinerary_missingDays_throwsAiServiceException() {
        responseBody = "{\"tripTitle\":\"x\",\"destination\":\"Goa\"}";

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generateItinerary(request()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("days");
    }

    @Test
    void generateItinerary_negativeCost_throwsAiServiceException() {
        responseBody = "{\"totalEstimatedCostInr\":-1,\"days\":[{\"day\":1}]}";

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generateItinerary(request()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void generateItinerary_malformedJson_throwsAiServiceException() {
        responseBody = "not-json";

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generateItinerary(request()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void generateItinerary_readTimeout_throwsAiServiceException() {
        responseDelayMs = 1_500;

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).generateItinerary(request()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void generateItinerary_unknownEnumValue_throwsAiServiceException() {
        responseBody = VALID_BODY.replace("\"PEAK\",\"multiplier\"", "\"MONSOON\",\"multiplier\"");

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generateItinerary(request()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void clarify_success_callsClarifyPath() {
        responseBody = "{\"questions\":[{\"id\":\"pace\",\"text\":\"Pace?\",\"type\":\"single_choice\",\"options\":[\"relaxed\"]}]}";

        AiClarifyResponse response = client(Duration.ofSeconds(5)).clarify(request());

        assertThat(receivedPath.get()).isEqualTo("/internal/v1/preferences/clarify");
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        assertThat(response.questions()).extracting(AiClarifyResponse.Question::id).containsExactly("pace");
    }

    @Test
    void refine_messageOnly_callsRefinePath() throws Exception {
        responseBody = "{\"assistantMessage\":\"Sure, swapped the museum.\",\"updatedPlan\":null}";

        AiRefineResponse response = client(Duration.ofSeconds(5)).refine(new AiRefineRequest(42L, request(),
                objectMapper.readTree("{\"days\":[]}"), List.of(new AiRefineRequest.Turn("USER", "hi")), "Swap day 2"));

        assertThat(receivedPath.get()).isEqualTo("/internal/v1/itineraries/refine");
        JsonNode sent = objectMapper.readTree(receivedBody.get());
        assertThat(sent.get("message").asText()).isEqualTo("Swap day 2");
        assertThat(sent.get("history").get(0).get("role").asText()).isEqualTo("USER");
        assertThat(response.assistantMessage()).startsWith("Sure");
        assertThat(response.updatedPlan()).isNull();
    }

    @Test
    void checkRoute_postsStopsAndParsesResponse() throws Exception {
        responseBody = """
                {"stops":[{"seq":1,"city":"Delhi","countryCode":"IN","nights":2,"known":true},
                          {"seq":2,"city":"Agra","countryCode":"IN","nights":1,"known":true}],
                 "legs":[{"fromCity":"Delhi","toCity":"Agra","roadKm":233,"withinLimit":true}],
                 "valid":true,"violations":[]}
                """;

        AiRouteResponse route = client(Duration.ofSeconds(5)).checkRoute(new AiRouteRequest("Mumbai",
                List.of(new AiStop("delhi", null, 2), new AiStop("agra", "IN", 1)), true));

        assertThat(receivedPath.get()).isEqualTo("/internal/v1/stops/route");
        JsonNode sent = objectMapper.readTree(receivedBody.get());
        assertThat(sent.get("optimizeOrder").asBoolean()).isTrue();
        assertThat(sent.get("stops").get(1).get("countryCode").asText()).isEqualTo("IN");
        assertThat(route.valid()).isTrue();
        assertThat(route.stops()).extracting(AiRouteResponse.Stop::city).containsExactly("Delhi", "Agra");
    }

    @Test
    void suggestStops_sendsTripAndLimit() throws Exception {
        responseBody = """
                {"suggestions":[{"city":"Agra","countryCode":"IN","nearStop":"Delhi","distanceKm":233,
                  "travelHours":4.5,"suggestedNights":2,"extraCost":{"min":5000,"expected":6000,"max":7000},
                  "currency":"INR","season":"OFF","reason":"Taj Mahal"}]}
                """;

        AiSuggestResponse response = client(Duration.ofSeconds(5)).suggestStops(new AiSuggestRequest(request(), 5));

        assertThat(receivedPath.get()).isEqualTo("/internal/v1/stops/suggest");
        assertThat(objectMapper.readTree(receivedBody.get()).get("limit").asInt()).isEqualTo(5);
        assertThat(objectMapper.readTree(receivedBody.get()).get("trip").get("allowWebHints").asBoolean()).isFalse();
        assertThat(response.suggestions()).singleElement().satisfies(s -> {
            assertThat(s.city()).isEqualTo("Agra");
            assertThat(s.season()).isEqualTo(Season.OFF);
        });
    }

    @Test
    void properties_toString_masksApiKey() {
        assertThat(properties(Duration.ofSeconds(1)).toString()).doesNotContain(TOKEN).contains("apiKey=***");
    }

    private AiServiceClient client(Duration readTimeout) {
        RestClient restClient = new AiServiceClientConfig()
                .aiServiceRestClient(RestClient.builder(), properties(readTimeout));
        return new AiServiceClient(restClient);
    }

    private AiServiceProperties properties(Duration readTimeout) {
        return new AiServiceProperties("http://127.0.0.1:" + server.getAddress().getPort(), TOKEN,
                Duration.ofSeconds(2), readTimeout);
    }

    private static AiGenerateTripRequest request() {
        return new AiGenerateTripRequest(42L, "Mumbai", "Goa", LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 14),
                2, new BigDecimal("50000.00"), "INR", "RELAXED", ComfortLevel.PREMIUM, List.of(TransportMode.TRAIN),
                List.of("beaches"), Map.of("pace", "relaxed"), List.of(), false);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
