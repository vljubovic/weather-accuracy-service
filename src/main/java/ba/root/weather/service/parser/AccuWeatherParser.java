package ba.root.weather.service.parser;

import ba.root.weather.entity.ForecastData;
import ba.root.weather.entity.Weather;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class AccuWeatherParser implements WeatherDataParser {
    private static final Logger logger = LoggerFactory.getLogger(AccuWeatherParser.class);
    private final ObjectMapper objectMapper;

    public AccuWeatherParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "AccuWeather";
    }

    @Override
    public List<ForecastData> parseForecastResponse(String cityName, String jsonResponse, Instant fetchTimestamp) {
        logger.info("Parsing AccuWeather forecast for {}", cityName);
        List<ForecastData> forecasts = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // The forecast data is in the "DailyForecasts" array
            if (!root.has("DailyForecasts") || !root.get("DailyForecasts").isArray()) {
                logger.error("Invalid AccuWeather response format: missing 'DailyForecasts' array");
                return Collections.emptyList();
            }

            for (JsonNode dailyForecast : root.get("DailyForecasts")) {
                try {
                    // Extract date from epoch time
                    long epochDate = dailyForecast.get("EpochDate").asLong();
                    LocalDate targetDate = Instant.ofEpochSecond(epochDate).atZone(ZoneOffset.UTC).toLocalDate();

                    // Extract min and max temperatures
                    JsonNode tempNode = dailyForecast.get("Temperature");
                    Double minTemp = tempNode.get("Minimum").get("Value").asDouble();
                    Double maxTemp = tempNode.get("Maximum").get("Value").asDouble();

                    // Extract weather phrase from the "Day" object
                    String iconPhrase = dailyForecast.get("Day").get("IconPhrase").asText();
                    Weather predictedWeather = mapToWeatherEnum(iconPhrase);

                    // Create and add the forecast data object
                    ForecastData forecast = new ForecastData(
                            getProviderName(),
                            cityName,
                            fetchTimestamp,
                            targetDate,
                            minTemp,
                            maxTemp,
                            predictedWeather
                    );
                    forecasts.add(forecast);

                } catch (Exception e) {
                    logger.warn("Error parsing a daily forecast entry for AccuWeather: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing AccuWeather JSON response for {}", cityName, e);
            return Collections.emptyList();
        }

        logger.info("Successfully parsed {} forecast entries for {} from AccuWeather", forecasts.size(), cityName);
        return forecasts;
    }

    /**
     * Maps AccuWeather's IconPhrase to our internal Weather enum.
     * This is a simplified mapping and can be expanded.
     */
    private Weather mapToWeatherEnum(String iconPhrase) {
        String lowerPhrase = iconPhrase.toLowerCase();

        if (lowerPhrase.contains("thunderstorm")) {
            return Weather.THUNDERSTORM;
        }
        if (lowerPhrase.contains("snow") || lowerPhrase.contains("sleet") || lowerPhrase.contains("flurries")) {
            return Weather.SNOW;
        }
        if (lowerPhrase.contains("rain") || lowerPhrase.contains("showers")) {
            return Weather.RAIN;
        }
        if (lowerPhrase.contains("fog") || lowerPhrase.contains("hazy")) {
            return Weather.FOG_MIST;
        }
        if (lowerPhrase.contains("cloudy") || lowerPhrase.contains("overcast")) {
            return Weather.CLOUDS;
        }
        if (lowerPhrase.contains("partly sunny") || lowerPhrase.contains("intermittent clouds") || lowerPhrase.contains("mostly cloudy")) {
            return Weather.PARTIAL_CLOUDS;
        }
        if (lowerPhrase.contains("sunny") || lowerPhrase.contains("clear")) {
            return Weather.CLEAR;
        }

        // Default fallback
        return Weather.CLEAR;
    }
}