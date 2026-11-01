package com.tripwise.entity;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One estimated cost line of an itinerary version (all v1 amounts are estimates, never live prices).
 */
@Entity
@Table(name = "trip_cost_items")
@Getter
@Setter
@NoArgsConstructor
public class TripCostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "itinerary_version", nullable = false)
    private Integer itineraryVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", nullable = false, length = 30)
    private CostCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", length = 20)
    private TransportMode mode;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "amount_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountMin;

    @Column(name = "amount_expected", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountExpected;

    @Column(name = "amount_max", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountMax;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "season", nullable = false, length = 10)
    private Season season;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", nullable = false, length = 20)
    private CostSource source = CostSource.ESTIMATE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "confidence", nullable = false, length = 10)
    private Confidence confidence;

    @Column(name = "assumptions")
    private String assumptions;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    // Multi-city only: stop (1..6) a stay belongs to, or transport leg index (0..6)
    @Column(name = "stop_seq")
    private Integer stopSeq;

    @Column(name = "leg_index")
    private Integer legIndex;

    // Web page backing a WEB_HINT price (http/https only)
    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
