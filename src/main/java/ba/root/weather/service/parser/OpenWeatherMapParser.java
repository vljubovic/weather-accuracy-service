package ba.root.weather.service.parser;

import ba.root.weather.entity.ForecastData;
import ba.root.weather.entity.Weather;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class OpenWeatherMapParser implements WeatherDataParser {
    private static final Logger logger = LoggerFactory.getLogger(OpenWeatherMapParser.class);
    private final ObjectMapper objectMapper;

    public OpenWeatherMapParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ForecastData> parseForecastResponse(String cityName, String jsonResponse, Instant fetchTimestamp) {
        logger.info("Parsing OpenWeatherMap forecast for {}", cityName);
        
        // Map to store daily aggregated forecast data
        Map<LocalDate, DailyForecastAggregator> dailyForecasts = new HashMap<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Check if the response contains valid data
            if (!root.has("list") || !root.get("list").isArray()) {
                logger.error("Invalid OpenWeatherMap response format: missing 'list' array");
                return Collections.emptyList();
            }

            // Process each 3-hour forecast
            for (JsonNode forecastEntry : root.get("list")) {
                try {
                    // Extract date (dt is in seconds since Unix epoch)
                    long epochSeconds = forecastEntry.get("dt").asLong();
                    LocalDate targetDate = Instant.ofEpochSecond(epochSeconds)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate();

                    // Get or create the daily aggregator for this date
                    DailyForecastAggregator dailyAggregator = dailyForecasts.computeIfAbsent(
                            targetDate, date -> new DailyForecastAggregator());

                    // Extract temperature data from the 'main' node
                    JsonNode mainNode = forecastEntry.get("main");
                    if (mainNode != null) {
                        if (mainNode.has("temp_min")) {
                            double tempMin = mainNode.get("temp_min").asDouble();
                            dailyAggregator.addMinTemp(tempMin);
                        }
                        
                        if (mainNode.has("temp_max")) {
                            double tempMax = mainNode.get("temp_max").asDouble();
                            dailyAggregator.addMaxTemp(tempMax);
                        }
                    }

                    // Extract weather data
                    if (forecastEntry.has("weather") && forecastEntry.get("weather").isArray() &&
                            !forecastEntry.get("weather").isEmpty()) {

                        JsonNode weatherNode = forecastEntry.get("weather").get(0);
                        String mainWeather = weatherNode.has("main") ? weatherNode.get("main").asText() : "";
                        int weatherId = weatherNode.has("id") ? weatherNode.get("id").asInt() : 800;

                        Weather weather = mapToWeatherEnum(mainWeather, weatherId);
                        dailyAggregator.addWeather(weather);
                    }

                } catch (Exception e) {
                    logger.warn("Error parsing forecast entry: {}", e.getMessage());
                    // Continue with next forecast entry
                }
            }

            // Convert the aggregated data to ForecastData objects
            return createForecastDataList(cityName, fetchTimestamp, dailyForecasts);

        } catch (Exception e) {
            logger.error("Error parsing OpenWeatherMap response", e);
            return Collections.emptyList();
        }
    }

    private List<ForecastData> createForecastDataList(
            String cityName, 
            Instant fetchTimestamp,
            Map<LocalDate, DailyForecastAggregator> dailyForecasts) {
        
        List<ForecastData> result = new ArrayList<>();
        
        for (Map.Entry<LocalDate, DailyForecastAggregator> entry : dailyForecasts.entrySet()) {
            LocalDate date = entry.getKey();
            DailyForecastAggregator aggregator = entry.getValue();
            
            // Create forecast data for this day
            ForecastData forecast = new ForecastData(
                    getProviderName(),
                    cityName,
                    fetchTimestamp,
                    date,
                    aggregator.getMinTemp(),
                    aggregator.getMaxTemp(),
                    aggregator.getMostFrequentWeather()
            );
            
            result.add(forecast);
            logger.debug("Created forecast for {}: {} - Min: {}°C, Max: {}°C, Weather: {}",
                    cityName, date, forecast.getPredictedMinTemp(), 
                    forecast.getPredictedMaxTemp(), forecast.getPredictedWeather());
        }
        
        return result;
    }

    @Override
    public String getProviderName() {
        return "OpenWeatherMap";
    }

    /**
     * Maps OpenWeatherMap weather data to our Weather enum
     */
    private Weather mapToWeatherEnum(String mainWeather, int weatherId) {
        // First check the main weather type
        switch (mainWeather) {
            case "Thunderstorm":
                return Weather.THUNDERSTORM;
            case "Drizzle":
            case "Rain":
                return Weather.RAIN;
            case "Snow":
                return Weather.SNOW;
            case "Atmosphere": // Includes mist, fog, haze, etc.
                return Weather.FOG_MIST;
            case "Clear":
                return Weather.CLEAR;
            case "Clouds":
                // Check cloud coverage based on ID
                if (weatherId == 801) { // few clouds
                    return Weather.PARTIAL_CLOUDS;
                } else if (weatherId == 802) { // scattered clouds
                    return Weather.PARTIAL_CLOUDS;
                } else if (weatherId == 803 || weatherId == 804) { // broken or overcast clouds
                    return Weather.CLOUDS;
                } else {
                    return Weather.PARTIAL_CLOUDS; // Default for other cloud types
                }
            default:
                return Weather.CLEAR; // Default case
        }
    }
    
    /**
     * Helper class to aggregate 3-hour forecast data into daily forecasts
     */
    private static class DailyForecastAggregator {
        @Getter
        private Double minTemp = null;
        @Getter
        private Double maxTemp = null;
        private final Map<Weather, Integer> weatherFrequency = new HashMap<>();
        
        public void addMinTemp(double temp) {
            if (minTemp == null || temp < minTemp) {
                minTemp = temp;
            }
        }
        
        public void addMaxTemp(double temp) {
            if (maxTemp == null || temp > maxTemp) {
                maxTemp = temp;
            }
        }
        
        public void addWeather(Weather weather) {
            weatherFrequency.put(weather, weatherFrequency.getOrDefault(weather, 0) + 1);
        }

        public Weather getMostFrequentWeather() {
            if (weatherFrequency.isEmpty()) {
                return Weather.CLEAR; // Default
            }
            
            return Collections.max(
                    weatherFrequency.entrySet(),
                    Map.Entry.comparingByValue()
            ).getKey();
        }
    }
}