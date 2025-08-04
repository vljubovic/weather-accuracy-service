package ba.root.weather.service;

import ba.root.weather.entity.ActualWeatherData;
import ba.root.weather.entity.Weather;
import ba.root.weather.repository.ActualWeatherDataRepository;
import ba.root.weather.repository.ForecastDataRepository;
import ba.root.weather.service.parser.WeatherDataParserFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataCollectorServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource configResource;

    @Mock
    private ForecastDataRepository forecastDataRepository;

    @Mock
    private WeatherDataParserFactory parserFactory;

    @Mock
    private ActualWeatherDataRepository actualWeatherDataRepository;

    private DataCollectorService dataCollectorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() throws IOException {
        // Setup the ObjectMapper
        dataCollectorService = new DataCollectorService(
                restTemplate,
                objectMapper,
                resourceLoader,
                actualWeatherDataRepository,
                forecastDataRepository,
                parserFactory
        );

        // Mock the resource loader to return our test config
        when(resourceLoader.getResource(eq("classpath:static/config.json")))
                .thenReturn(configResource);

        // Create a sample config.json content
        String configJson = """
                {
                  "cities": [
                    {
                      "name": "Sarajevo",
                      "latitude": 43.8563,
                      "longitude": 18.4131,
                      "icao_code": "LQSA"
                    }
                  ],
                  "actualWeatherSource": {
                    "name": "Aviation Weather (METAR)",
                    "url": "https://aviationweather.gov/api/data/metar?ids={}&format=json"
                  }
                }""";

        // Mock the input stream to return our config content
        InputStream inputStream = new java.io.ByteArrayInputStream(configJson.getBytes());
        when(configResource.getInputStream()).thenReturn(inputStream);
    }

    @Test
    public void testFetchActualWeather_Success() {
        // 1. Setup test data
        String mockResponse = """
                [
                  {
                    "metar_id": 433994517,
                    "icaoId": "LQSA",
                    "receiptTime": "2025-07-31 15:53:12",
                    "obsTime": 1753952400,
                    "reportTime": "2025-07-31 16:00:00",
                    "temp": 24.0,
                    "dewp": 11.0,
                    "wndir": 330,
                    "wndspd": 5,
                    "visibility": 10.0,
                    "altim": 1016.3,
                    "slp": 1016.1,
                    "qcField": 6,
                    "wxString": "NOSIG",
                    "presTend": 2.1,
                    "rawOb": "LQSA 311600Z 33005KT 9999 SCT060 24/11 Q1016 NOSIG"
                  }
                ]""";

        // 2. Mock the REST call
        when(restTemplate.getForObject(
                eq("https://aviationweather.gov/api/data/metar?ids=LQSA&format=json"),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 3. Execute the method
        dataCollectorService.fetchActualWeather();

        // 4. Capture and verify the saved entity
        ArgumentCaptor<ActualWeatherData> weatherDataCaptor = ArgumentCaptor.forClass(ActualWeatherData.class);
        verify(actualWeatherDataRepository).save(weatherDataCaptor.capture());

        ActualWeatherData capturedData = weatherDataCaptor.getValue();
        assertNotNull(capturedData);
        assertEquals("Sarajevo", capturedData.getCity());
        assertEquals(24.0, capturedData.getActualTemperature());

        // Calculate expected timestamp from the test data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime expectedDateTime = LocalDateTime.parse("2025-07-31 15:53:12", formatter);
        Instant expectedTimestamp = expectedDateTime.toInstant(ZoneOffset.UTC);

        assertEquals(expectedTimestamp, capturedData.getMeasurementTimestamp());
        assertEquals(Weather.PARTIAL_CLOUDS, capturedData.getWeather()); // NOSIG should map to CLEAR, however we have SCT in rawOb
        assertEquals(0.0, capturedData.getActualPrecipitation()); // No precipitation
    }

    @Test
    public void testFetchActualWeather_WithRain() {
        // 1. Setup test data with rain
        String mockResponse = """
                [
                  {
                    "metar_id": 433994517,
                    "icaoId": "LQSA",
                    "receiptTime": "2025-07-31 15:53:12",
                    "obsTime": 1753952400,
                    "reportTime": "2025-07-31 16:00:00",
                    "temp": 18.5,
                    "dewp": 16.0,
                    "wndir": 180,
                    "wndspd": 10,
                    "visibility": 5.0,
                    "altim": 1010.3,
                    "slp": 1010.1,
                    "qcField": 6,
                    "wxString": "-RA",
                    "presTend": -1.5,
                    "rawOb": "LQSA 311600Z 18010KT 5000 RA BKN030 18/16 Q1010"
                  }
                ]""";

        // 2. Mock the REST call
        when(restTemplate.getForObject(
                eq("https://aviationweather.gov/api/data/metar?ids=LQSA&format=json"),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 3. Execute the method
        dataCollectorService.fetchActualWeather();

        // 4. Capture and verify the saved entity
        ArgumentCaptor<ActualWeatherData> weatherDataCaptor = ArgumentCaptor.forClass(ActualWeatherData.class);
        verify(actualWeatherDataRepository).save(weatherDataCaptor.capture());

        ActualWeatherData capturedData = weatherDataCaptor.getValue();
        assertNotNull(capturedData);
        assertEquals("Sarajevo", capturedData.getCity());
        assertEquals(18.5, capturedData.getActualTemperature());
        assertEquals(Weather.RAIN, capturedData.getWeather());
        assertEquals(0.5, capturedData.getActualPrecipitation()); // Light rain
    }

    @Test
    public void testFetchActualWeather_WithThunderstorm() {
        // 1. Setup test data with thunderstorm
        String mockResponse = """
                [
                  {
                    "metar_id": 433994517,
                    "icaoId": "LQSA",
                    "receiptTime": "2025-07-31 15:53:12",
                    "obsTime": 1753952400,
                    "reportTime": "2025-07-31 16:00:00",
                    "temp": 22.0,
                    "dewp": 19.0,
                    "wndir": 220,
                    "wndspd": 15,
                    "visibility": 2.0,
                    "altim": 1008.5,
                    "slp": 1008.3,
                    "qcField": 6,
                    "wxString": "TSRA",
                    "presTend": -2.8,
                    "rawOb": "LQSA 311600Z 22015KT 2000 TSRA OVC020CB 22/19 Q1008"
                  }
                ]""";

        // 2. Mock the REST call
        when(restTemplate.getForObject(
                eq("https://aviationweather.gov/api/data/metar?ids=LQSA&format=json"),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 3. Execute the method
        dataCollectorService.fetchActualWeather();

        // 4. Capture and verify the saved entity
        ArgumentCaptor<ActualWeatherData> weatherDataCaptor = ArgumentCaptor.forClass(ActualWeatherData.class);
        verify(actualWeatherDataRepository).save(weatherDataCaptor.capture());

        ActualWeatherData capturedData = weatherDataCaptor.getValue();
        assertNotNull(capturedData);
        assertEquals("Sarajevo", capturedData.getCity());
        assertEquals(22.0, capturedData.getActualTemperature());
        assertEquals(Weather.THUNDERSTORM, capturedData.getWeather());
        assertEquals(10.0, capturedData.getActualPrecipitation()); // Heavy rain with thunderstorm
    }

    @Test
    public void testFetchActualWeather_ApiError() {
        // 1. Mock the REST call to throw an exception
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("API Connection Error"));

        // 2. Execute the method - should not throw exception
        assertDoesNotThrow(() -> dataCollectorService.fetchActualWeather());

        // 3. Verify no data was saved
        verify(actualWeatherDataRepository, never()).save(any());
    }

    @Test
    public void testFetchActualWeather_MultipleWeatherReports() {
        // 1. Setup test data with multiple weather reports
        String mockResponse = """
                [
                  {
                    "metar_id": 433994517,
                    "icaoId": "LQSA",
                    "receiptTime": "2025-07-31 15:53:12",
                    "obsTime": 1753952400,
                    "reportTime": "2025-07-31 16:00:00",
                    "temp": 24.0,
                    "dewp": 11.0,
                    "wndir": 330,
                    "wndspd": 5,
                    "visibility": 10.0,
                    "altim": 1016.3,
                    "slp": 1016.1,
                    "qcField": 6,
                    "wxString": "NOSIG",
                    "presTend": 2.1,
                    "rawOb": "LQSA 311600Z 33005KT 9999 SCT060 24/11 Q1016 NOSIG"
                  },
                  {
                    "metar_id": 433994518,
                    "icaoId": "LQSA",
                    "receiptTime": "2025-07-31 14:53:12",
                    "obsTime": 1753948800,
                    "reportTime": "2025-07-31 15:00:00",
                    "temp": 25.0,
                    "dewp": 12.0,
                    "wndir": 320,
                    "wndspd": 7,
                    "visibility": 10.0,
                    "altim": 1016.0,
                    "slp": 1015.8,
                    "qcField": 6,
                    "wxString": "SCT",
                    "presTend": 1.8,
                    "rawOb": "LQSA 311500Z 32007KT 9999 SCT050 25/12 Q1016"
                  }
                ]""";

        // 2. Mock the REST call
        when(restTemplate.getForObject(
                eq("https://aviationweather.gov/api/data/metar?ids=LQSA&format=json"),
                eq(String.class)
        )).thenReturn(mockResponse);

        // 3. Execute the method
        dataCollectorService.fetchActualWeather();

        // 4. Verify that two entities were saved
        verify(actualWeatherDataRepository, times(2)).save(any(ActualWeatherData.class));
    }
}
