package com.tripwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/**
 * One stop of a multi-city trip, in visiting order ({@code seq} 1..6).
 */
@Entity
@Table(name = "trip_stops")
@Getter
@Setter
@NoArgsConstructor
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "city", nullable = false, length = 255)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "IN";

    @Column(name = "nights", nullable = false)
    private Integer nights;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
