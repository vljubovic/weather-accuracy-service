
package ba.root.weather.repository;

import ba.root.weather.entity.AccuracyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * Repository for accessing and manipulating AccuracyScore entities
 */
@Repository
public interface AccuracyScoreRepository extends JpaRepository<AccuracyScore, Long> {
    /**
     * Delete all accuracy scores for a specific target date
     */
    void deleteByTargetDate(LocalDate targetDate);
}