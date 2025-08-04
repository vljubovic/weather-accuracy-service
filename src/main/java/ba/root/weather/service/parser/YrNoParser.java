package ba.root.weather.service.parser;

import ba.root.weather.entity.ForecastData;
import ba.root.weather.entity.Weather;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class YrNoParser implements WeatherDataParser {
    private static final Logger logger = LoggerFactory.getLogger(YrNoParser.class);
    private final ObjectMapper objectMapper;

    public YrNoParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ForecastData> parseForecastResponse(String cityName, String jsonResponse, Instant fetchTimestamp) {
        logger.info("Parsing YR.NO forecast for {}", cityName);
        List<ForecastData> forecasts = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Check if the response contains valid data
            if (!root.has("properties") || !root.get("properties").has("timeseries") ||
                    !root.get("properties").get("timeseries").isArray()) {
                logger.error("Invalid YR.NO response format: missing 'properties.timeseries' array");
                return forecasts;
            }

            // Group forecast data by day
            Map<LocalDate, DailyForecastData> dailyForecasts = new HashMap<>();
            JsonNode timeseriesNode = root.get("properties").get("timeseries");

            for (JsonNode timeseriesEntry : timeseriesNode) {
                try {
                    // Extract time and convert to date
                    String timeStr = timeseriesEntry.get("time").asText();
                    ZonedDateTime dateTime = ZonedDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME);
                    LocalDate date = dateTime.toLocalDate();

                    // Create or get the daily forecast entry
                    DailyForecastData dailyData = dailyForecasts.computeIfAbsent(date,
                            DailyForecastData::new);

                    // Extract temperature
                    JsonNode instantDetails = timeseriesEntry.get("data").get("instant").get("details");
                    if (instantDetails.has("air_temperature")) {
                        double temperature = instantDetails.get("air_temperature").asDouble();
                        dailyData.addTemperature(temperature);
                    }

                    // Check if we have next_12_hours data
                    if (timeseriesEntry.get("data").has("next_12_hours") &&
                            timeseriesEntry.get("data").get("next_12_hours").has("summary") &&
                            timeseriesEntry.get("data").get("next_12_hours").get("summary").has("symbol_code")) {

                        String symbolCode = timeseriesEntry.get("data")
                                .get("next_12_hours").get("summary").get("symbol_code").asText();

                        // Get the hour of day to identify midnight and noon forecasts
                        int hour = dateTime.getHour();

                        // Capture weather at midnight or noon
                        if (hour == 0 || hour == 12) {
                            String normalizedSymbolCode = normalizeSymbolCode(symbolCode);
                            if (hour == 0) {
                                dailyData.setMidnightWeather(normalizedSymbolCode);
                            } else { // hour == 12
                                dailyData.setNoonWeather(normalizedSymbolCode);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing timeseries entry: {}", e.getMessage());
                    // Continue with next entry
                }
            }

            // Convert the daily forecast data to ForecastData objects
            for (DailyForecastData dailyData : dailyForecasts.values()) {
                // Skip days with no temperature data
                if (!dailyData.hasTemperatureData()) {
                    continue;
                }

                // Create forecast data object
                ForecastData forecast = new ForecastData(
                        getProviderName(),
                        cityName,
                        fetchTimestamp,
                        dailyData.getDate(),
                        dailyData.getMinTemperature(),
                        dailyData.getMaxTemperature(),
                        mapToWeatherEnum(dailyData.getWorseWeather())
                );

                forecasts.add(forecast);
                logger.debug("Parsed forecast for {}: {} - Min: {}°C, Max: {}°C, Weather: {}",
                        cityName, dailyData.getDate(), dailyData.getMinTemperature(),
                        dailyData.getMaxTemperature(), forecast.getPredictedWeather());
            }

        } catch (Exception e) {
            logger.error("Error parsing YR.NO response", e);
        }

        return forecasts;
    }

    @Override
    public String getProviderName() {
        return "YR.NO";
    }

    /**
     * Helper class to track forecast data for a specific day
     */
    private static class DailyForecastData {
        @Getter
        private final LocalDate date;
        private final List<Double> temperatures = new ArrayList<>();
        @Setter
        private String midnightWeather;
        @Setter
        private String noonWeather;

        public DailyForecastData(LocalDate date) {
            this.date = date;
        }

        public void addTemperature(double temp) {
            temperatures.add(temp);
        }

        public boolean hasTemperatureData() {
            return !temperatures.isEmpty();
        }

        public Double getMinTemperature() {
            return temperatures.stream()
                    .min(Double::compare)
                    .orElse(null);
        }

        public Double getMaxTemperature() {
            return temperatures.stream()
                    .max(Double::compare)
                    .orElse(null);
        }

        /**
         * Get the "worse" weather between midnight and noon.
         * If one is null, return the other. If both are null, return null.
         */
        public String getWorseWeather() {
            if (midnightWeather == null && noonWeather == null) {
                return null;
            } else if (midnightWeather == null) {
                return noonWeather;
            } else if (noonWeather == null) {
                return midnightWeather;
            } else {
                return getWorseWeatherSymbol(midnightWeather, noonWeather);
            }
        }
    }

    /**
     * Remove _day and _night suffixes from symbol codes
     */
    private String normalizeSymbolCode(String symbolCode) {
        return symbolCode
                .replace("_day", "")
                .replace("_night", "")
                .replace("_polartwilight", "");
    }

    /**
     * Compare two weather symbols and return the "worse" one based on severity
     */
    private static String getWorseWeatherSymbol(String symbol1, String symbol2) {
        // Weather severity ranking (from best to worst)
        List<String> severityRanking = Arrays.asList(
                "clearsky",
                "fair",
                "partlycloudy",
                "cloudy",
                "fog",
                "lightrain",
                "rain",
                "heavyrain",
                "lightsnow",
                "snow",
                "heavysnow",
                "sleet",
                "heavysleet",
                "lightsleetshowers",
                "sleetshowers",
                "heavysleetshowers",
                "lightrainshowers",
                "rainshowers",
                "heavyrainshowers",
                "lightsnowshowers",
                "snowshowers",
                "heavysnowshowers",
                "lightrainshowersandthunder",
                "rainshowersandthunder",
                "heavyrainshowersandthunder",
                "lightsleetshowersandthunder",
                "sleetshowersandthunder",
                "heavysleetshowersandthunder",
                "lightsnowshowersandthunder",
                "snowshowersandthunder",
                "heavysnowshowersandthunder",
                "lightrainandthunder",
                "rainandthunder",
                "heavyrainandthunder",
                "lightsleetandthunder",
                "sleetandthunder",
                "heavysleetandthunder",
                "lightsnowandthunder",
                "snowandthunder",
                "heavysnowandthunder"
        );

        // Get the indices of the symbols in the severity ranking
        int index1 = severityRanking.indexOf(symbol1);
        int index2 = severityRanking.indexOf(symbol2);

        // If a symbol is not found in the ranking, give it a middle severity
        if (index1 == -1) index1 = severityRanking.size() / 2;
        if (index2 == -1) index2 = severityRanking.size() / 2;

        // Return the symbol with higher severity (higher index)
        return (index1 >= index2) ? symbol1 : symbol2;
    }

    /**
     * Maps YR.NO weather symbols to our Weather enum
     */
    private Weather mapToWeatherEnum(String symbolCode) {
        if (symbolCode == null) {
            return Weather.CLEAR; // Default
        }

        String normalizedCode = normalizeSymbolCode(symbolCode.toLowerCase());

        if (normalizedCode.contains("thunder")) {
            return Weather.THUNDERSTORM;
        } else if (normalizedCode.contains("sleet") || normalizedCode.contains("snow")) {
            return Weather.SNOW;
        } else if (normalizedCode.contains("rain") || normalizedCode.contains("shower")) {
            return Weather.RAIN;
        } else if (normalizedCode.contains("fog")) {
            return Weather.FOG_MIST;
        } else if (normalizedCode.contains("cloudy")) {
            return Weather.CLOUDS;
        } else if (normalizedCode.contains("partlycloudy") || normalizedCode.contains("fair")) {
            return Weather.PARTIAL_CLOUDS;
        } else if (normalizedCode.contains("clearsky")) {
            return Weather.CLEAR;
        } else {
            logger.warn("Unknown weather symbol code: {}", symbolCode);
            return Weather.CLEAR; // Default for unknown symbols
        }
    }
}