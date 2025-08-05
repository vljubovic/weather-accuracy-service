package ba.root.weather.dto;

import ba.root.weather.entity.PrecipitationScoreType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccuracyScoreDto {
    private String providerName;
    private LocalDate targetDate;
    private int forecastHorizon;
    private double minTempDeviation;
    private double maxTempDeviation;
    private PrecipitationScoreType precipitationScore;
}