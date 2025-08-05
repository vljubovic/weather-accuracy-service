
package ba.root.weather.repository;

import ba.root.weather.entity.AccuracyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for accessing and manipulating AccuracyScore entities
 */
@Repository
public interface AccuracyScoreRepository extends JpaRepository<AccuracyScore, Long> {
    /**
     * Delete all accuracy scores for a specific target date
     */
    void deleteByTargetDate(LocalDate targetDate);

    List<AccuracyScore> findByCityAndTargetDateAfter(String city, LocalDate date);

    List<AccuracyScore> findByCityAndTargetDate(String city, LocalDate date);

    List<AccuracyScore> findByCityAndForecastHorizonAndTargetDateAfter(String city, int horizon, LocalDate date);

    @Query("SELECT DISTINCT a.forecastHorizon, a.targetDate FROM AccuracyScore a WHERE a.city = ?1")
    List<Object[]> findDistinctHorizonsAndDatesByCity(String city);
}