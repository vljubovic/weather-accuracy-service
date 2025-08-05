package ba.root.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScoreDto {
    private String providerName;
    private double overallScore;
    private double averageTempDeviation;
    private double precipitationAccuracy;
}