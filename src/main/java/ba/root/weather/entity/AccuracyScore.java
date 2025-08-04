package ba.root.weather.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entity representing the accuracy of weather forecasts compared to actual weather data.
 * This is used to evaluate the performance of different weather providers.
 */
@Entity
@Table(name = "accuracy_score",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider_name", "city", "target_date", "forecast_horizon"})
        })
@Data
@NoArgsConstructor
public class AccuracyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the weather data provider (e.g., "OpenWeatherMap", "YrNo")
     */
    @Column(name = "provider_name", nullable = false)
    private String providerName;

    /**
     * Name of the city for which the forecast was made
     */
    @Column(nullable = false)
    private String city;

    /**
     * The date for which the weather was forecasted
     */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /**
     * How many hours in advance the forecast was made
     * Example: if forecast for 2025-08-03 was fetched on 2025-08-01 18:00,
     * the horizon is 30 hours (assuming day starts at midnight)
     */
    @Column(name = "forecast_horizon", nullable = false)
    private Integer forecastHorizon;

    /**
     * Deviation from actual measured minimum daily temperature
     * Lower value means better accuracy
     */
    @Column(name = "min_temp_score", nullable = false)
    private Double minTempScore;

    /**
     * Deviation from actual measured maximum daily temperature
     * Lower value means better accuracy
     */
    @Column(name = "max_temp_score", nullable = false)
    private Double maxTempScore;

    /**
     * Score for precipitation forecast accuracy
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "precipitation_score")
    private PrecipitationScoreType precipitationScore;

    /**
     * Constructor with all fields except ID
     */
    public AccuracyScore(String providerName, String city, LocalDate targetDate,
                         Integer forecastHorizon, Double minTempScore,
                         Double maxTempScore, PrecipitationScoreType precipitationScore) {
        this.providerName = providerName;
        this.city = city;
        this.targetDate = targetDate;
        this.forecastHorizon = forecastHorizon;
        this.minTempScore = minTempScore;
        this.maxTempScore = maxTempScore;
        this.precipitationScore = precipitationScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccuracyScore that = (AccuracyScore) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AccuracyScore{" +
                "id=" + id +
                ", providerName='" + providerName + '\'' +
                ", city='" + city + '\'' +
                ", targetDate=" + targetDate +
                ", forecastHorizon=" + forecastHorizon +
                ", minTempScore=" + minTempScore +
                ", maxTempScore=" + maxTempScore +
                ", precipitationScore=" + precipitationScore +
                '}';
    }
}