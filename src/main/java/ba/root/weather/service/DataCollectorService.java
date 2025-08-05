package ba.root.weather.service;

import ba.root.weather.entity.ActualWeatherData;
import ba.root.weather.entity.ForecastData;
import ba.root.weather.entity.Weather;
import ba.root.weather.repository.ActualWeatherDataRepository;
import ba.root.weather.repository.ForecastDataRepository;
import ba.root.weather.service.parser.WeatherDataParser;
import ba.root.weather.service.parser.WeatherDataParserFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataCollectorService {
    private static final Logger logger = LoggerFactory.getLogger(DataCollectorService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ForecastDataRepository forecastDataRepository;
    private final WeatherDataParserFactory parserFactory;
    private final ActualWeatherDataRepository actualWeatherDataRepository;

    private final Map<String, String> locationKeyCache = new ConcurrentHashMap<>();

    @Autowired
    public DataCollectorService(RestTemplate restTemplate, 
                               ObjectMapper objectMapper,
                               ResourceLoader resourceLoader,
                               ActualWeatherDataRepository actualWeatherDataRepository,
                               ForecastDataRepository forecastDataRepository,
                               WeatherDataParserFactory parserFactory) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.actualWeatherDataRepository = actualWeatherDataRepository;
        this.forecastDataRepository = forecastDataRepository;
        this.parserFactory = parserFactory;
    }
    
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void fetchActualWeather() {
        logger.info("Fetching actual weather...");
        try {
            // 1. Load and parse the config.json file
            JsonNode config = loadConfig();
            
            // 2. Get the URL template from config
            String urlTemplate = config.get("actualWeatherSource").get("url").asText();
            
            // 3. Get the list of cities with their ICAO codes
            List<String> icaoCodes = new ArrayList<>();
            JsonNode citiesNode = config.get("cities");
            for (JsonNode city : citiesNode) {
                icaoCodes.add(city.get("icao_code").asText());
            }
            
            // 4. Create the actual URL by replacing the placeholder
            String actualUrl = urlTemplate.replace("{}", String.join(",", icaoCodes));
            
            // 5. Call the API and get the response
            String response = restTemplate.getForObject(actualUrl, String.class);
            
            // 6. Parse the response JSON
            JsonNode weatherData = objectMapper.readTree(response);
            
            // 7. Process each city's weather data
            for (JsonNode data : weatherData) {
                String icaoId = data.get("icaoId").asText();
                String cityName = getCityNameByIcaoCode(config, icaoId);
                if (cityName == null) {
                    logger.warn("City not found for ICAO code: {}", icaoId);
                    continue;
                }
                
                // Parse receipt time
                String receiptTimeStr = data.get("receiptTime").asText();
                LocalDateTime receiptDateTime = LocalDateTime.parse(receiptTimeStr, DATE_TIME_FORMATTER);
                Instant measurementTimestamp = receiptDateTime.toInstant(ZoneOffset.UTC);
                
                // Get temperature
                Double temperature = data.has("temp") ? data.get("temp").asDouble() : null;
                
                // Parse weather string to determine precipitation and weather type
                String wxString = data.has("wxString") ? data.get("wxString").asText() : "";
                Weather weatherType;

                if (wxString == null || wxString.isEmpty() || wxString.equals("null")) {
                    weatherType = Weather.CLEAR;
                } else {
                    // wxString is present, parse it as usual
                    weatherType = parseWeatherType(wxString);
                }

                // Check cloud cover
                if (weatherType == Weather.CLEAR) {
                    String rawOb = data.has("rawOb") ? data.get("rawOb").asText() : "";
                    weatherType = parseCloudCover(rawOb);
                }

                Double precipitation = estimatePrecipitation(wxString);
                
                // Create and save the ActualWeatherData entity
                ActualWeatherData weatherDataEntity = new ActualWeatherData(
                        cityName,
                        measurementTimestamp,
                        temperature,
                        precipitation,
                        weatherType
                );
                
                actualWeatherDataRepository.save(weatherDataEntity);
                logger.info("Saved actual weather data for {}: {}°C, {}", cityName, temperature, weatherType);
            }

        } catch (Exception e) {
            logger.error("Error fetching actual weather data", e);
        }
    }

    @Scheduled(cron = "0 0 */6 * * *") // Run every 6 hours
    public void fetchForecasts() {
        logger.info("Fetching weather forecasts...");
        try {
            // Load the configuration
            JsonNode config = loadConfig();
            
            // Get the list of cities
            JsonNode citiesNode = config.get("cities");
            if (citiesNode == null || !citiesNode.isArray()) {
                logger.error("Invalid or missing 'cities' configuration");
                return;
            }
            
            // Get the list of providers
            JsonNode providersNode = config.get("providers");
            if (providersNode == null || !providersNode.isArray()) {
                logger.error("Invalid or missing 'providers' configuration");
                return;
            }
            
            // Process each provider
            for (JsonNode provider : providersNode) {
                String providerName = provider.get("name").asText();
                String urlTemplate = provider.get("url").asText();
                
                logger.info("Processing provider: {}", providerName);
                
                // Check if we have a parser for this provider
                if (!parserFactory.hasParser(providerName)) {
                    logger.warn("No parser configured for provider: {}", providerName);
                    continue;
                }
                
                // Get the parser for this provider
                WeatherDataParser parser = parserFactory.getParser(providerName);
                
                // Process each city for this provider
                for (JsonNode city : citiesNode) {
                    String cityName = city.get("name").asText();
                    
                    try {
                        String actualUrl;
                        // Special handling for AccuWeather's two-step API process
                        if ("AccuWeather".equals(providerName)) {
                            String locationKey = getLocationKey(city, provider);
                            if (locationKey == null) {
                                logger.error("Could not retrieve location key for {} from AccuWeather. Skipping.", cityName);
                                continue;
                            }
                            logger.info("Location key for {} is {}", cityName, locationKey);
                            // Create a temporary node with the location key to build the forecast URL
                            JsonNode providerWithLocation = ((com.fasterxml.jackson.databind.node.ObjectNode) provider)
                                    .put("locationKey", locationKey);
                            actualUrl = createUrlWithParameters(provider.get("url").asText(), city, providerWithLocation);
                        } else {
                            // Create the actual URL by replacing placeholders
                            actualUrl = createUrlWithParameters(provider.get("url").asText(), city, provider);
                        }

                        // Skip if URL couldn't be created
                        if (actualUrl == null) {
                            continue;
                        }
                        
                        logger.info("Fetching forecast for {} from {}", cityName, providerName);
                        
                        // Call the API and get the response
                        String response = restTemplate.getForObject(actualUrl, String.class);
                        
                        // Parse the response using the appropriate parser
                        Instant fetchTimestamp = Instant.now();
                        List<ForecastData> forecasts = parser.parseForecastResponse(cityName, response, fetchTimestamp);
                        
                        // Save forecasts to the database
                        if (forecasts != null && !forecasts.isEmpty()) {
                            forecastDataRepository.saveAll(forecasts);
                            logger.info("Saved {} forecast entries for {} from {}", 
                                    forecasts.size(), cityName, providerName);
                        } else {
                            logger.warn("No forecast data parsed for {} from {}", cityName, providerName);
                        }
                        
                    } catch (RestClientException e) {
                        logger.error("Error fetching forecast for {} from {}: {}", 
                                cityName, providerName, e.getMessage());
                    } catch (Exception e) {
                        logger.error("Unexpected error processing forecast for {} from {}", 
                                cityName, providerName, e);
                    }
                }
            }
            
            logger.info("Forecast fetching completed");
            
        } catch (IOException e) {
            logger.error("Error loading configuration", e);
        } catch (Exception e) {
            logger.error("Unexpected error in forecast fetching", e);
        }
    }

    // Helper method for AccuWeather
    private String getLocationKey(JsonNode city, JsonNode provider) {
        String cityName = city.get("name").asText();
        if (locationKeyCache.containsKey(cityName)) {
            logger.info("Found AccuWeather location key for {} in cache.", cityName);
            return locationKeyCache.get(cityName);
        }

        String locationUrlTemplate = provider.get("locationUrl").asText();
        String locationUrl = createUrlWithParameters(locationUrlTemplate, city, provider);
        if (locationUrl == null) return null;

        try {
            String response = restTemplate.getForObject(locationUrl, String.class);
            JsonNode locationResponse = objectMapper.readTree(response);

            // The response is an array, get the first result's "Key"
            if (locationResponse.isArray() && !locationResponse.isEmpty()) {
                String key = locationResponse.get(0).get("Key").asText();
                locationKeyCache.put(cityName, key);
                return key;
            } else if (locationResponse.has("Key")) { // Sometimes it's not an array
                String key = locationResponse.get("Key").asText();
                locationKeyCache.put(cityName, key);
                return key;
            }
            logger.error("AccuWeather location response did not contain a 'Key' field.");
            return null;
        } catch (Exception e) {
            logger.error("Failed to fetch or parse AccuWeather location key", e);
            return null;
        }
    }

    // Helper method to create URL with parameters from city and provider
    private String createUrlWithParameters(String urlTemplate, JsonNode city, JsonNode provider) {
        // Find all placeholders in the URL template
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(urlTemplate);
        
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String paramValue = null;

            // Prioritize reading 'apiKey' from an environment variable for security
            if ("apiKey".equals(paramName)) {
                String providerName = provider.get("name").asText();
                if ("AccuWeather".equals(providerName)) {
                    paramValue = System.getenv("ACCUWEATHER_API_KEY");
                } else if ("OpenWeatherMap".equals(providerName)) {
                    paramValue = System.getenv("OPENWEATHERMAP_API_KEY");
                }

                if (paramValue == null || paramValue.isEmpty()) {
                    logger.warn("Environment variable for {} apiKey not set. Falling back to config.json.", providerName);
                    if (provider.has(paramName)) {
                        paramValue = provider.get(paramName).asText();
                    }
                }
            } else {
                // Other parameters (latitude, longitude, etc.)
                if (city.has(paramName)) {
                    paramValue = city.get(paramName).asText();
                } else if (provider.has(paramName)) {
                    paramValue = provider.get(paramName).asText();
                }
            }
            
            if (paramValue != null) {
                // Replace the placeholder with the actual value
                matcher.appendReplacement(result, paramValue);
            } else {
                logger.error("Parameter '{}' not found in config or environment for URL: {}", paramName, urlTemplate);
                return null;
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }

    // Keep existing methods...
    private String getCityNameByIcaoCode(JsonNode config, String icaoCode) {
        JsonNode citiesNode = config.get("cities");
        for (JsonNode city : citiesNode) {
            if (city.get("icao_code").asText().equals(icaoCode)) {
                return city.get("name").asText();
            }
        }
        return null;
    }
    
    private Weather parseWeatherType(String wxString) {
        if (wxString == null || wxString.isEmpty()) {
            return Weather.CLEAR;  // Default to clear if no weather string is provided
        }
        
        wxString = wxString.toUpperCase();
        
        // Check for precipitation types
        if (wxString.contains("TS")) {
            return Weather.THUNDERSTORM;
        }

        if (wxString.contains("SN") || wxString.contains("SG") || wxString.contains("IC") ||
                wxString.contains("PL") || wxString.contains("GS")) {
            return Weather.SNOW;
        }

        if (wxString.contains("RA") || wxString.contains("DZ") || wxString.contains("GR") ||
        wxString.contains("SH") || wxString.contains("UP")) {
            return Weather.RAIN;
        }
        
        // Check for fog, mist, and similar conditions
        if (wxString.contains("FG") || wxString.contains("BR") || wxString.contains("HZ") || 
        wxString.contains("DU") || wxString.contains("SA") || wxString.contains("FU") || 
        wxString.contains("VA") || wxString.contains("PO") || wxString.contains("SQ") || 
        wxString.contains("FC") || wxString.contains("SS") || wxString.contains("DS")) {
            return Weather.FOG_MIST;
        }

        // If no specific condition is detected, default to clear
        return Weather.CLEAR;
    }
    
    /**
     * Estimates precipitation amount based on METAR weather string.
     * Returns an estimated value in millimeters (mm).
     */
    private Double estimatePrecipitation(String wxString) {
        if (wxString == null || wxString.isEmpty()) {
            return 0.0;  // No precipitation if no weather string is provided
        }
        
        wxString = wxString.toUpperCase();
        
        // Check intensity indicators
        boolean isLight = wxString.contains("-");
        boolean isHeavy = wxString.contains("+");
        
        // Estimate based on precipitation type and intensity
        if (wxString.contains("TS")) {
            // Thunderstorm
            if (isLight) return 5.0;  // Light thunderstorm: ~5mm
            if (isHeavy) return 15.0; // Heavy thunderstorm: ~15mm
            return 10.0; // Moderate thunderstorm: ~10mm
        }

        if (wxString.contains("SH")) {
            // Showers
            if (isLight) return 1.0;  // Light showers: ~1mm
            if (isHeavy) return 8.0;  // Heavy showers: ~8mm
            return 3.0;  // Moderate showers: ~3mm
        }

        if (wxString.contains("RA") || wxString.contains("DZ")) {
            // Rain or drizzle
            if (isLight) return 0.5;  // Light rain: ~0.5mm
            if (isHeavy) return 4.0;  // Heavy rain: ~4mm
            return 2.0;  // Moderate rain: ~2mm
        }
        
        if (wxString.contains("SN") || wxString.contains("SG") || wxString.contains("IC") ||
            wxString.contains("PL") || wxString.contains("GS")) {
            // Snow or similar - convert to water equivalent
            if (isLight) return 0.2;  // Light snow: ~0.2mm water equivalent
            if (isHeavy) return 2.0;  // Heavy snow: ~2mm water equivalent
            return 0.8;  // Moderate snow: ~0.8mm water equivalent
        }
        
        if (wxString.contains("GR")) {
            // Hail
            if (isLight) return 1.0;  // Light hail: ~1mm
            if (isHeavy) return 10.0; // Heavy hail: ~10mm
            return 4.0;  // Moderate hail: ~4mm
        }
        
        // No precipitation for fog, mist, and similar conditions
        if (wxString.contains("FG") || wxString.contains("BR") || wxString.contains("HZ") || 
            wxString.contains("DU") || wxString.contains("SA") || wxString.contains("FU") || 
            wxString.contains("VA") || wxString.contains("PO") || wxString.contains("SQ") || 
            wxString.contains("FC") || wxString.contains("SS") || wxString.contains("DS")) {
            return 0.0;
        }
        
        // Default case: no recognized precipitation
        return 0.0;
    }

    private Weather parseCloudCover(String rawOb) {
        Weather weatherType = Weather.CLEAR;
        for (String part : rawOb.split(" ") ) {
            if (part.contains("OVC") || part.contains("BKN")) {
                if (part.matches("BKN\\d+")) {
                    int altitude = Integer.parseInt(part.substring(3));
                    if (altitude < 80) return Weather.CLOUDS;
                    weatherType = Weather.PARTIAL_CLOUDS;
                } else
                    return Weather.CLOUDS;
            }
            if (part.contains("FEW") || part.contains("SCT")) {
                weatherType = Weather.PARTIAL_CLOUDS;
            }
        }
        return weatherType;
    }
    
    // Keep your original methods for config loading, URL creation, etc.
    private JsonNode loadConfig() throws IOException {
        // Implementation remains the same
        try (InputStream inputStream = resourceLoader.getResource("classpath:static/config.json").getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }
}