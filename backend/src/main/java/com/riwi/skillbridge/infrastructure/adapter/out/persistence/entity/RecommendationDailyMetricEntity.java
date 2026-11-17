package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the recommendation_daily_metrics table.
 * Each row aggregates all recommendation and booking activity for one offering on one calendar day.
 * The unique constraint on (date, offering_id) ensures no duplicate rows are created.
 */
@Entity
@Table(
        name = "recommendation_daily_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_metric_offering_date",
                columnNames = {"date", "offering_id"}
        )
)
public class RecommendationDailyMetricEntity {

    @Id
    private UUID id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;

    @Column(name = "recommendations", nullable = false)
    private long recommendations;

    @Column(name = "unique_users", nullable = false)
    private long uniqueUsers;

    @Column(name = "bookings", nullable = false)
    private long bookings;

    @Column(name = "cancellations", nullable = false)
    private long cancellations;

    @Column(name = "conversion_rate", nullable = false)
    private double conversionRate;

    @Column(name = "trend_score", nullable = false)
    private double trendScore;

    @Column(name = "forecast_next_7_days", nullable = false)
    private double forecastNext7Days;

    protected RecommendationDailyMetricEntity() {}

    public RecommendationDailyMetricEntity(UUID id, LocalDate date, UUID offeringId,
                                           long recommendations, long uniqueUsers,
                                           long bookings, long cancellations,
                                           double conversionRate, double trendScore,
                                           double forecastNext7Days) {
        this.id = id;
        this.date = date;
        this.offeringId = offeringId;
        this.recommendations = recommendations;
        this.uniqueUsers = uniqueUsers;
        this.bookings = bookings;
        this.cancellations = cancellations;
        this.conversionRate = conversionRate;
        this.trendScore = trendScore;
        this.forecastNext7Days = forecastNext7Days;
    }

    public UUID getId() { return id; }
    public LocalDate getDate() { return date; }
    public UUID getOfferingId() { return offeringId; }
    public long getRecommendations() { return recommendations; }
    public long getUniqueUsers() { return uniqueUsers; }
    public long getBookings() { return bookings; }
    public long getCancellations() { return cancellations; }
    public double getConversionRate() { return conversionRate; }
    public double getTrendScore() { return trendScore; }
    public double getForecastNext7Days() { return forecastNext7Days; }

    // Mutators used by the persistence adapter for incremental updates
    public void setRecommendations(long v) { this.recommendations = v; }
    public void setUniqueUsers(long v) { this.uniqueUsers = v; }
    public void setBookings(long v) { this.bookings = v; }
    public void setCancellations(long v) { this.cancellations = v; }
    public void setConversionRate(double v) { this.conversionRate = v; }
    public void setTrendScore(double v) { this.trendScore = v; }
    public void setForecastNext7Days(double v) { this.forecastNext7Days = v; }
}
