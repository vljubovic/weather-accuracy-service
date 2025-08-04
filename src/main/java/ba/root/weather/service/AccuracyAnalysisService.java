package ba.root.weather.service;

import ba.root.weather.entity.*;
import ba.root.weather.repository.AccuracyScoreRepository;
import ba.root.weather.repository.ActualWeatherDataRepository;
import ba.root.weather.repository.ForecastDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for analyzing the accuracy of weather forecasts
 * by comparing them with actual weather data.
 */
@Service
public class AccuracyAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(AccuracyAnalysisService.class);
    
    // Define Sarajevo timezone for consistent handling
    private static final ZoneId SARAJEVO_ZONE = ZoneId.of("Europe/Sarajevo");

    private final ForecastDataRepository forecastRepository;
    private final ActualWeatherDataRepository actualDataRepository;
    private final AccuracyScoreRepository accuracyRepository;
    
    @Autowired
    public AccuracyAnalysisService(
            ForecastDataRepository forecastRepository,
            ActualWeatherDataRepository actualDataRepository,
            AccuracyScoreRepository accuracyRepository) {
        this.forecastRepository = forecastRepository;
        this.actualDataRepository = actualDataRepository;
        this.accuracyRepository = accuracyRepository;
    }

    /**
     * Analyzes forecast accuracy for a given date, once actual data becomes available.
     * Calculates scores for all providers and all forecast horizons.
     *
     * @param date The date to analyze forecasts for (in Sarajevo local time)
     * @return The number of accuracy scores generated
     */
    @Transactional
    public int analyzeAccuracyForDate(LocalDate date) {
        logger.info("Starting accuracy analysis for date: {} (Sarajevo time)", date);
        
        // First check if we have complete actual data for this date
        if (!isActualDataCompleteForDate(date)) {
            logger.warn("Actual weather data is not complete for date: {}. Skipping analysis.", date);
            return 0;
        }
        
        // Get actual min/max temperatures and precipitation status for the date
        Map<String, DailyActualWeather> actualWeatherByCity = getActualWeatherForDate(date);
        
        if (actualWeatherByCity.isEmpty()) {
            logger.warn("No actual weather data found for date: {}. Skipping analysis.", date);
            return 0;
        }
        
        // Delete any existing accuracy scores for this date to avoid duplicates
        accuracyRepository.deleteByTargetDate(date);
        
        // Get all forecasts for this target date
        List<ForecastData> forecasts = forecastRepository.findByTargetDate(date);
        logger.info("Found {} forecasts for date: {}", forecasts.size(), date);
        
        // Group forecasts by provider, city, and fetch timestamp
        Map<String, Map<String, Map<Instant, ForecastData>>> groupedForecasts = 
                groupForecasts(forecasts);
        
        // Generate accuracy scores
        Map<String, AccuracyScore> accuracyScores = new HashMap<>();
        
        for (Map.Entry<String, Map<String, Map<Instant, ForecastData>>> providerEntry : 
                groupedForecasts.entrySet()) {
            String providerName = providerEntry.getKey();
            
            for (Map.Entry<String, Map<Instant, ForecastData>> cityEntry : 
                    providerEntry.getValue().entrySet()) {
                String city = cityEntry.getKey();
                
                // Skip if we don't have actual data for this city
                if (!actualWeatherByCity.containsKey(city)) {
                    logger.warn("No actual weather data for city: {}. Skipping accuracy analysis.", city);
                    continue;
                }
                
                DailyActualWeather actualWeather = actualWeatherByCity.get(city);
                
                for (Map.Entry<Instant, ForecastData> forecastEntry : 
                        cityEntry.getValue().entrySet()) {
                    Instant fetchTimestamp = forecastEntry.getKey();
                    ForecastData forecast = forecastEntry.getValue();
                    
                    // Calculate forecast horizon in hours
                    int forecastHorizon = calculateForecastHorizon(fetchTimestamp, date);
                    // We don't care about forecasts in the past
                    if (forecastHorizon < 0) continue;
                    
                    // Generate accuracy score
                    AccuracyScore score = generateAccuracyScore(
                            forecast, actualWeather, forecastHorizon);
                    String mapKey = providerName + ":" + city + ":" + date + ":" + forecastHorizon;
                    
                    accuracyScores.put(mapKey, score);
                }
            }
        }
        
        // Save all generated scores
        accuracyRepository.saveAll(accuracyScores.values());
        logger.info("Generated and saved {} accuracy scores for date: {}", 
                accuracyScores.size(), date);
        
        return accuracyScores.size();
    }
    
    /**
     * Check if we have complete actual weather data for the entire date
     * Uses Sarajevo timezone for day boundaries
     */
    private boolean isActualDataCompleteForDate(LocalDate date) {
        // Calculate the start and end of the day in Sarajevo time, then convert to UTC for database queries
        ZonedDateTime startOfDayLocal = date.atStartOfDay(SARAJEVO_ZONE);
        ZonedDateTime endOfDayLocal = date.plusDays(1).atStartOfDay(SARAJEVO_ZONE);
        
        // Convert to UTC Instants for database queries
        Instant startOfDayUtc = startOfDayLocal.toInstant();
        Instant endOfDayUtc = endOfDayLocal.toInstant();
        
        logger.debug("Checking data completeness between {} and {} UTC", 
                startOfDayUtc, endOfDayUtc);
        
        // Get all cities we have forecast data for
        List<String> cities = forecastRepository.findByTargetDate(date).stream()
                .map(ForecastData::getCity)
                .distinct()
                .toList();
        
        if (cities.isEmpty()) {
            logger.info("No forecast data found for any city on date: {}", date);
            return false;
        }
        
        // For each city, check if we have data for the last hour of the day
        boolean isComplete = true;
        for (String city : cities) {
            // Check for data in the last hour of the day in Sarajevo time
            Instant lastHourStart = endOfDayUtc.minus(Duration.ofHours(1));
            List<ActualWeatherData> lastHourData = actualDataRepository
                    .findByCityAndMeasurementTimestampBetween(city, lastHourStart, endOfDayUtc);
            
            if (lastHourData.isEmpty()) {
                logger.info("Missing actual weather data for city {} on date {} in the last hour", 
                        city, date);
                isComplete = false;
            }
        }
        
        return isComplete;
    }
    
    /**
     * Retrieve actual weather data (min/max temperatures and precipitation) for the given date
     * Uses Sarajevo timezone for day boundaries
     */
    private Map<String, DailyActualWeather> getActualWeatherForDate(LocalDate date) {
        // Calculate the start and end of the day in Sarajevo time, then convert to UTC
        ZonedDateTime startOfDayLocal = date.atStartOfDay(SARAJEVO_ZONE);
        ZonedDateTime endOfDayLocal = date.plusDays(1).atStartOfDay(SARAJEVO_ZONE);
        
        // Convert to UTC Instants for database queries
        Instant startOfDayUtc = startOfDayLocal.toInstant();
        Instant endOfDayUtc = endOfDayLocal.toInstant();
        
        logger.debug("Getting actual weather between {} and {} UTC", 
                startOfDayUtc, endOfDayUtc);
        
        // Get all actual weather data for the date
        List<ActualWeatherData> actualDataList = actualDataRepository
                .findByMeasurementTimestampBetween(startOfDayUtc, endOfDayUtc);
        
        // Group by city and calculate min/max temperatures and precipitation status
        Map<String, DailyActualWeather> result = new HashMap<>();
        
        // Group by city
        Map<String, List<ActualWeatherData>> dataByCity = actualDataList.stream()
                .collect(Collectors.groupingBy(ActualWeatherData::getCity));
        
        for (Map.Entry<String, List<ActualWeatherData>> entry : dataByCity.entrySet()) {
            String city = entry.getKey();
            List<ActualWeatherData> cityData = entry.getValue();
            
            if (cityData.isEmpty()) {
                continue;
            }
            
            // Calculate min/max temperatures
            double minTemp = cityData.stream()
                    .filter(d -> d.getActualTemperature() != null)
                    .mapToDouble(ActualWeatherData::getActualTemperature)
                    .min()
                    .orElse(Double.NaN);
            
            double maxTemp = cityData.stream()
                    .filter(d -> d.getActualTemperature() != null)
                    .mapToDouble(ActualWeatherData::getActualTemperature)
                    .max()
                    .orElse(Double.NaN);
            
            // Determine if there was any precipitation
            boolean hadPrecipitation = cityData.stream()
                    .anyMatch(d -> d.getActualPrecipitation() != null && 
                            d.getActualPrecipitation() > 0.0);
            
            // Create a daily actual weather object
            DailyActualWeather dailyWeather = new DailyActualWeather(minTemp, maxTemp, hadPrecipitation);
            result.put(city, dailyWeather);
        }
        
        return result;
    }
    
    /**
     * Group forecasts by provider, city, and fetch timestamp
     * If multiple forecasts exist for the same provider/city/timestamp, keep only the latest one by ID
     */
    private Map<String, Map<String, Map<Instant, ForecastData>>> groupForecasts(List<ForecastData> forecasts) {
        Map<String, Map<String, Map<Instant, ForecastData>>> result = new HashMap<>();
        
        for (ForecastData forecast : forecasts) {
            // Add provider if not present
            result.putIfAbsent(forecast.getProviderName(), new HashMap<>());
            
            // Add city if not present for this provider
            Map<String, Map<Instant, ForecastData>> citiesMap = result.get(forecast.getProviderName());
            citiesMap.putIfAbsent(forecast.getCity(), new HashMap<>());
            
            // Add or replace forecast for this timestamp
            Map<Instant, ForecastData> timestampsMap = citiesMap.get(forecast.getCity());
            
            // If there is already a forecast for this timestamp, keep the one with higher ID
            // (which is likely the more recent entry in the database)
            ForecastData existingForecast = timestampsMap.get(forecast.getFetchTimestamp());
            if (existingForecast == null || existingForecast.getId() < forecast.getId()) {
                timestampsMap.put(forecast.getFetchTimestamp(), forecast);
            }
        }
        
        return result;
    }
    
    /**
     * Calculate the forecast horizon in hours
     * Takes into account Sarajevo timezone
     * 
     * @param fetchTimestamp when the forecast was made (UTC)
     * @param targetDate the date the forecast was for (Sarajevo local date)
     * @return number of hours between fetch time and the start of the target date in Sarajevo time
     */
    private int calculateForecastHorizon(Instant fetchTimestamp, LocalDate targetDate) {
        // Convert target date to instant (beginning of the day in Sarajevo time)
        Instant targetDateInstant = targetDate.atStartOfDay(SARAJEVO_ZONE).toInstant();
        
        // Calculate hours between fetch time and target date
        Duration duration = Duration.between(fetchTimestamp, targetDateInstant);
        
        // If negative (forecast was made after the date started), return 0
        //return Math.max(0, (int) duration.toHours());
        return (int)duration.toHours();
    }
    
    /**
     * Generate an accuracy score for a forecast compared to actual weather
     */
    private AccuracyScore generateAccuracyScore(
            ForecastData forecast, 
            DailyActualWeather actualWeather, 
            int forecastHorizon) {
        
        // Calculate temperature deviations (absolute differences)
        double minTempScore = Double.isNaN(actualWeather.minTemp) || forecast.getPredictedMinTemp() == null
                ? 0.0 : (forecast.getPredictedMinTemp() - actualWeather.minTemp);
        
        double maxTempScore = Double.isNaN(actualWeather.maxTemp) || forecast.getPredictedMaxTemp() == null
                ? 0.0 : (forecast.getPredictedMaxTemp() - actualWeather.maxTemp);
        
        // Determine precipitation score
        PrecipitationScoreType precipScore = calculatePrecipitationScore(
                forecast.getPredictedWeather(), actualWeather.hadPrecipitation);
        
        // Create and return the accuracy score
        return new AccuracyScore(
                forecast.getProviderName(),
                forecast.getCity(),
                forecast.getTargetDate(),
                forecastHorizon,
                minTempScore,
                maxTempScore,
                precipScore
        );
    }
    
    /**
     * Calculate precipitation score based on predicted weather and actual precipitation
     */
    private PrecipitationScoreType calculatePrecipitationScore(Weather predictedWeather, boolean actualPrecipitation) {
        // Determine if the forecast predicted precipitation
        boolean predictedPrecipitation = false;
        if (predictedWeather != null) {
            predictedPrecipitation = switch (predictedWeather) {
                case RAIN, SNOW, THUNDERSTORM -> true;
                default -> false;
            };
        }
        
        // Calculate the precipitation score
        if (predictedPrecipitation && actualPrecipitation) {
            return PrecipitationScoreType.TRUE_POSITIVE;
        } else if (predictedPrecipitation) {
            return PrecipitationScoreType.FALSE_POSITIVE;
        } else if (actualPrecipitation) {
            return PrecipitationScoreType.FALSE_NEGATIVE;
        } else {
            return PrecipitationScoreType.TRUE_NEGATIVE;
        }
    }
    
    /**
     * Helper class to store daily actual weather data
     */
    private static class DailyActualWeather {
        private final double minTemp;
        private final double maxTemp;
        private final boolean hadPrecipitation;
        
        public DailyActualWeather(double minTemp, double maxTemp, boolean hadPrecipitation) {
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.hadPrecipitation = hadPrecipitation;
        }
    }
}