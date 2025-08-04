package ba.root.weather.repository;

import ba.root.weather.entity.ForecastData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ForecastDataRepository extends JpaRepository<ForecastData, Long> {
    // Find forecasts for a specific target date
    List<ForecastData> findByTargetDate(LocalDate targetDate);
}