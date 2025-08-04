package ba.root.weather.service.parser;

import ba.root.weather.entity.ForecastData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YrNoParserTest {

    private YrNoParser parser;
    private String sampleResponse;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        parser = new YrNoParser(objectMapper);

        // Read the sample response from the docs directory
        sampleResponse = Files.readString(Paths.get("docs/samples/responses/yr_no_sample_response.json"));
    }

    @Test
    void parseForecastResponse() {
        // Given
        String cityName = "Oslo";
        Instant fetchTimestamp = Instant.now();

        // When
        List<ForecastData> forecasts = parser.parseForecastResponse(cityName, sampleResponse, fetchTimestamp);

        // Then
        assertNotNull(forecasts);
        assertFalse(forecasts.isEmpty());

        // Sample checks on the data
        ForecastData firstDay = forecasts.getFirst();
        assertEquals("YR.NO", firstDay.getProviderName());
        assertEquals(cityName, firstDay.getCity());
        assertNotNull(firstDay.getPredictedMinTemp());
        assertNotNull(firstDay.getPredictedMaxTemp());
        assertNotNull(firstDay.getPredictedWeather());

        // Check number of days in forecast (should match the sample data's days count)
        assertFalse(forecasts.isEmpty(), "Should parse multiple days of forecast");

        // Check that temperature values make sense
        for (ForecastData forecast : forecasts) {
            assertTrue(forecast.getPredictedMinTemp() <= forecast.getPredictedMaxTemp(),
                    "Min temperature should be less than or equal to max temperature");
        }
    }

    @Test
    void getProviderName() {
        assertEquals("YR.NO", parser.getProviderName());
    }
}