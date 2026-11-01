package com.tripwise.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tripwise.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * In-memory token-bucket rate limiting (single instance). Runs after {@link JwtAuthenticationFilter} so AI
 * endpoints can be limited per user. Created by {@link SecurityConfig}; deliberately not a Spring bean.
 * Client IPs come from {@code getRemoteAddr()}, which honours X-Forwarded-For only from trusted internal proxies
 * ({@code server.forward-headers-strategy: native}).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern AI_PATH =
            Pattern.compile("^/api/v1/trips/\\d+/(generate|clarify|messages|stops/suggest)$");

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String key = null;
        RateLimitProperties.Rule rule = null;

        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")) {
            key = "auth:" + request.getRemoteAddr();
            rule = properties.auth();
        } else if (AI_PATH.matcher(path).matches()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                key = "ai:" + user.id();
                rule = properties.ai();
            }
        }

        if (key != null) {
            RateLimitProperties.Rule limit = rule;
            ConsumptionProbe probe = buckets.get(key, k -> newBucket(limit)).tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                reject(request, response, probe.getNanosToWaitForRefill());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static Bucket newBucket(RateLimitProperties.Rule rule) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()))
                .build();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long nanosToWait)
            throws IOException {
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanosToWait) + 1);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later.");
        problem.setTitle("Too many requests");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
