package com.tripwise.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    // Nullable only for trips created before V3; required by the API for new trips
    @Column(name = "origin", length = 255)
    private String origin;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "travellers_count", nullable = false)
    private Integer travellersCount;

    @Column(name = "budget_amount", precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "travel_style", length = 50)
    private String travelStyle;

    // VARCHAR keeps the mapping portable (H2 would otherwise expect a native ENUM column)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private TripStatus status = TripStatus.DRAFT;

    // Stored as a JSON array (JSONB on PostgreSQL, JSON on H2)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", nullable = false)
    private List<String> preferences = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "comfort_level", nullable = false, length = 20)
    private ComfortLevel comfortLevel = ComfortLevel.STANDARD;

    // Allowed modes for getting there; empty means "any"
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transport_modes", nullable = false)
    private List<TransportMode> transportModes = new ArrayList<>();

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    // Answers to clarifying questions, reused by regenerate and chat refine
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "planning_answers", nullable = false)
    private Map<String, String> planningAnswers = new LinkedHashMap<>();

    // Multi-city stops in visiting order; empty for single-destination trips
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<TripStop> stops = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
