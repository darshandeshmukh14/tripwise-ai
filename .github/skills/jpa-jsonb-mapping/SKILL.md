---
name: jpa-jsonb-mapping
description: Map TripWise JPA entities (User, Trip, TripItinerary, ChatMessage) to the Flyway schema, including PostgreSQL JSONB columns, timestamps, enums and relationships, with Hibernate 6. Use when creating or changing entities or JSONB structures.
---
# Mapping rules (Hibernate 6.6, Boot 3.5)
- `@Entity @Table(name = "trips")`, Lombok `@Getter @Setter @NoArgsConstructor`; no `@Data`; exclude lazy relations from `toString`/`equals`.
- ID: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`.
- Relations: `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")`. Itinerary: `@OneToOne(fetch = LAZY) @JoinColumn(name = "trip_id", unique = true)` on `TripItinerary`. Avoid bidirectional collections unless needed.
- Timestamps: `created_at`/`updated_at` are DB-defaulted and trigger-maintained -> map with `@Column(insertable = false, updatable = false)` + `@Generated` (Hibernate `@Generated(event = {INSERT, UPDATE})`), or set via `@CreationTimestamp`/`@UpdateTimestamp` - pick one approach consistently. Type `Instant`/`OffsetDateTime`.
- Money: `BigDecimal` with `@Column(precision = 12, scale = 2)`.
- Enums: `@Enumerated(EnumType.STRING)`; values must satisfy DB CHECKs (`sender_role` = `USER|ASSISTANT|SYSTEM`; `trips.status` default `DRAFT`).
- Optimistic locking: `trip_itineraries.version` is a business version (plan revision), not necessarily `@Version` - decide explicitly; don't use JPA `@Version` without agreeing semantics.

# JSONB
- `@JdbcTypeCode(SqlTypes.JSON)` with **no** `columnDefinition` - PostgreSQLDialect maps it to `jsonb`, H2Dialect to `JSON`, so `validate` passes on both. No extra library needed.
- Field type: a typed record (`TripPreferences`, `ItineraryPlan`) when the shape is known; `Map<String, Object>` or Jackson `JsonNode` when owned by the AI service and evolving.
- `trips.preferences` is `NOT NULL DEFAULT '{}'` -> initialise to an empty object/record in Java.
- `trip_itineraries.plan_data` is `NOT NULL` -> validate before save.
- Querying JSONB: native query with `@>`/`->>` is PostgreSQL-only and fails on the temporary H2 profile - prefer filtering in Java or flag it; keep such queries in the repository and still filter by `user_id`.
