package com.tripwise.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

@Entity
@Table(name = "trip_itineraries")
@Getter
@Setter
@NoArgsConstructor
public class TripItinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    /**
     * Plan revision number (incremented on regeneration); not a JPA optimistic-lock version.
     */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "total_estimated_cost", precision = 12, scale = 2)
    private BigDecimal totalEstimatedCost;

    // Currency of all amounts of this version (the trip currency at generation time)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    // Units of `currency` per 1 INR used for this version; null for plans generated before V7
    @Column(name = "fx_rate", precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "fx_as_of")
    private Instant fxAsOf;

    // AI-generated plan; shape is owned by the AI service, so it is kept as raw JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_data", nullable = false)
    private JsonNode planData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
