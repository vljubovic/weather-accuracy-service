package ba.root.weather.service.parser;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WeatherDataParserFactory {
    private final Map<String, WeatherDataParser> parsers = new HashMap<>();

    public WeatherDataParserFactory(List<WeatherDataParser> parserList) {
        // Register all parsers by their provider name
        for (WeatherDataParser parser : parserList) {
            parsers.put(parser.getProviderName(), parser);
        }
    }

    /**
     * Get the appropriate parser for the given provider
     *
     * @param providerName Name of the provider
     * @return Parser for the provider, or null if not found
     */
    public WeatherDataParser getParser(String providerName) {
        return parsers.get(providerName);
    }

    /**
     * Check if a parser exists for the given provider
     *
     * @param providerName Name of the provider
     * @return True if a parser exists, false otherwise
     */
    public boolean hasParser(String providerName) {
        return parsers.containsKey(providerName);
    }
}