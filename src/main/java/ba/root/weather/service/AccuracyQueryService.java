package ba.root.weather.service;

import ba.root.weather.dto.AccuracyScoreDto;
import ba.root.weather.dto.FilterOptionsDto;
import ba.root.weather.dto.ProviderScoreDto;
import ba.root.weather.entity.AccuracyScore;
import ba.root.weather.entity.PrecipitationScoreType;
import ba.root.weather.repository.AccuracyScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccuracyQueryService {

    private final AccuracyScoreRepository accuracyRepository;

    @Autowired
    public AccuracyQueryService(AccuracyScoreRepository accuracyRepository) {
        this.accuracyRepository = accuracyRepository;
    }

    public List<ProviderScoreDto> getRankedProviderSummary(String city, int days, Integer horizon, LocalDate targetDate) {
        List<AccuracyScore> scores = fetchScores(city, days, horizon, targetDate);

        // Group scores by provider
        Map<String, List<AccuracyScore>> scoresByProvider = scores.stream()
                .collect(Collectors.groupingBy(AccuracyScore::getProviderName));

        // Calculate aggregated scores for each provider
        return scoresByProvider.entrySet().stream()
                .map(entry -> {
                    String providerName = entry.getKey();
                    List<AccuracyScore> providerScores = entry.getValue();

                    double avgTempDeviation = providerScores.stream()
                            .mapToDouble(s -> (Math.abs(s.getMinTempScore()) + Math.abs(s.getMaxTempScore())) / 2.0)
                            .average()
                            .orElse(0.0);

                    long totalPrecipScores = providerScores.stream()
                            .filter(s -> s.getPrecipitationScore() != null)
                            .count();

                    long correctPrecipScores = providerScores.stream()
                            .filter(s -> s.getPrecipitationScore() == PrecipitationScoreType.TRUE_POSITIVE ||
                                    s.getPrecipitationScore() == PrecipitationScoreType.TRUE_NEGATIVE)
                            .count();

                    double precipAccuracy = (totalPrecipScores == 0) ? 0.0 : (double) correctPrecipScores / totalPrecipScores;

                    // A simple overall score: 100 minus temp deviation, plus bonus for precip accuracy
                    double overallScore = (100 - (avgTempDeviation * 10)) + (precipAccuracy * 10);

                    return new ProviderScoreDto(providerName, overallScore, avgTempDeviation, precipAccuracy);
                })
                .sorted(Comparator.comparing(ProviderScoreDto::getOverallScore).reversed())
                .collect(Collectors.toList());
    }

    public List<AccuracyScoreDto> getDetailedScores(String city, int days, Integer horizon, LocalDate targetDate) {
        return fetchScores(city, days, horizon, targetDate).stream()
                .map(score -> new AccuracyScoreDto(
                        score.getProviderName(),
                        score.getTargetDate(),
                        score.getForecastHorizon(),
                        score.getMinTempScore(),
                        score.getMaxTempScore(),
                        score.getPrecipitationScore()
                ))
                .sorted(Comparator.comparing(AccuracyScoreDto::getTargetDate).reversed()
                        .thenComparing(AccuracyScoreDto::getForecastHorizon))
                .collect(Collectors.toList());
    }

    public FilterOptionsDto getFilterOptions(String city) {
        List<Object[]> results = accuracyRepository.findDistinctHorizonsAndDatesByCity(city);
        List<Integer> horizons = results.stream().map(r -> (Integer) r[0]).distinct().sorted().collect(Collectors.toList());
        List<String> dates = results.stream().map(r -> r[1].toString()).distinct().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        return new FilterOptionsDto(horizons, dates);
    }

    private List<AccuracyScore> fetchScores(String city, int days, Integer horizon, LocalDate targetDate) {
        if (targetDate != null) {
            return accuracyRepository.findByCityAndTargetDate(city, targetDate);
        }
        if (horizon != null) {
            LocalDate startDate = LocalDate.now().minusDays(days);
            return accuracyRepository.findByCityAndForecastHorizonAndTargetDateAfter(city, horizon, startDate);
        }
        LocalDate startDate = LocalDate.now().minusDays(days);
        return accuracyRepository.findByCityAndTargetDateAfter(city, startDate);
    }
}