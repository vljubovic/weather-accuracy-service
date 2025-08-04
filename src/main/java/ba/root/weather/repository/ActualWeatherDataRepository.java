package ba.root.weather.repository;

import ba.root.weather.entity.ActualWeatherData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActualWeatherDataRepository extends JpaRepository<ActualWeatherData, Long> {
    
    // Find actual weather data for a city between two timestamps
    List<ActualWeatherData> findByCityAndMeasurementTimestampBetween(
            String city, Instant startTime, Instant endTime);
    
    // Find all actual weather data between two timestamps
    List<ActualWeatherData> findByMeasurementTimestampBetween(
            Instant startTime, Instant endTime);
    
    // Find the most recent weather data for a city
    Optional<ActualWeatherData> findFirstByCityOrderByMeasurementTimestampDesc(String city);
}