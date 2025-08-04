package ba.root.weather.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Setter
@Getter
@Entity
@Table(name = "forecast_data")
public class ForecastData {

    // Getters and Setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(nullable = false)
    private String city;

    @Column(name = "fetch_timestamp", nullable = false)
    private Instant fetchTimestamp;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "predicted_min_temp")
    private Double predictedMinTemp;

    @Column(name = "predicted_max_temp")
    private Double predictedMaxTemp;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_weather")
    private Weather predictedWeather;

    // Default constructor required by JPA
    public ForecastData() {
    }

    // Constructor with all fields
    public ForecastData(String providerName, String city, Instant fetchTimestamp,
                        LocalDate targetDate, Double predictedMinTemp, Double predictedMaxTemp,
                        Weather predictedWeather) {
        this.providerName = providerName;
        this.city = city;
        this.fetchTimestamp = fetchTimestamp;
        this.targetDate = targetDate;
        this.predictedMinTemp = predictedMinTemp;
        this.predictedMaxTemp = predictedMaxTemp;
        this.predictedWeather = predictedWeather;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForecastData that = (ForecastData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ForecastData{" +
                "id=" + id +
                ", providerName='" + providerName + '\'' +
                ", city='" + city + '\'' +
                ", fetchTimestamp=" + fetchTimestamp +
                ", targetDate=" + targetDate +
                ", predictedMinTemp=" + predictedMinTemp +
                ", predictedMaxTemp=" + predictedMaxTemp +
                ", predictedWeather=" + predictedWeather +
                '}';
    }
}