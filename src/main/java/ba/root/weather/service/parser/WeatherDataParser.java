package ba.root.weather.service.parser;

import ba.root.weather.entity.ForecastData;

import java.time.Instant;
import java.util.List;

public interface WeatherDataParser {
    /**
     * Parse the provider response and convert it to ForecastData objects
     *
     * @param cityName Name of the city for which forecast was requested
     * @param jsonResponse JSON response from the API
     * @param fetchTimestamp Timestamp when the forecast was fetched
     * @return List of ForecastData objects
     */
    List<ForecastData> parseForecastResponse(String cityName, String jsonResponse, Instant fetchTimestamp);

    /**
     * Get the name of the provider this parser is for
     *
     * @return Provider name
     */
    String getProviderName();
}