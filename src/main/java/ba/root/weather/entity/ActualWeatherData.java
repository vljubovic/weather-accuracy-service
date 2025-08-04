package ba.root.weather.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Setter
@Getter
@Entity
@Table(name = "actual_weather_data")
public class ActualWeatherData {

    // Getters and Setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(name = "measurement_timestamp", nullable = false)
    private Instant measurementTimestamp;

    @Column(name = "actual_temperature")
    private Double actualTemperature;

    @Column(name = "actual_precipitation")
    private Double actualPrecipitation;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "weather")
    private Weather weather;

    // Default constructor required by JPA
    public ActualWeatherData() {
    }

    // Constructor with all fields
    public ActualWeatherData(String city, Instant measurementTimestamp,
                             Double actualTemperature, Double actualPrecipitation,
                             Weather weather) {
        this.city = city;
        this.measurementTimestamp = measurementTimestamp;
        this.actualTemperature = actualTemperature;
        this.actualPrecipitation = actualPrecipitation;
        this.weather = weather;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActualWeatherData that = (ActualWeatherData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ActualWeatherData{" +
                "id=" + id +
                ", city='" + city + '\'' +
                ", measurementTimestamp=" + measurementTimestamp +
                ", actualTemperature=" + actualTemperature +
                ", actualPrecipitation=" + actualPrecipitation +
                ", weather=" + weather +
                '}';
    }
}