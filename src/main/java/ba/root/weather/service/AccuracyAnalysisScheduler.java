package ba.root.weather.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduler for running accuracy analysis on a regular basis
 */
@Component
public class AccuracyAnalysisScheduler {
    private static final Logger logger = LoggerFactory.getLogger(AccuracyAnalysisScheduler.class);
    private static final ZoneId SARAJEVO_ZONE = ZoneId.of("Europe/Sarajevo");
    
    private final AccuracyAnalysisService accuracyService;
    
    @Autowired
    public AccuracyAnalysisScheduler(AccuracyAnalysisService accuracyService) {
        this.accuracyService = accuracyService;
    }
    
    /**
     * Run accuracy analysis for yesterday's forecasts
     * Scheduled to run daily at 3:00 AM CEST/CET (Sarajevo time)
     * This ensures we have complete data for the previous day
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Europe/Sarajevo")
    public void analyzeYesterdayForecasts() {
        // Get yesterday's date based on Sarajevo's timezone
        LocalDate yesterday = LocalDate.now(SARAJEVO_ZONE).minusDays(1);
        logger.info("Running scheduled accuracy analysis for date: {}", yesterday);
        
        try {
            int scores = accuracyService.analyzeAccuracyForDate(yesterday);
            logger.info("Scheduled analysis complete. Generated {} accuracy scores for {}", 
                    scores, yesterday);
        } catch (Exception e) {
            logger.error("Error during scheduled accuracy analysis", e);
        }
    }
}