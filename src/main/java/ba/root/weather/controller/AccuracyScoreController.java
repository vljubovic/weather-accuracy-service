package ba.root.weather.controller;

import ba.root.weather.dto.AccuracyScoreDto;
import ba.root.weather.dto.FilterOptionsDto;
import ba.root.weather.dto.ProviderScoreDto;
import ba.root.weather.service.AccuracyQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accuracy")
public class AccuracyScoreController {

    private final AccuracyQueryService accuracyQueryService;

    @Autowired
    public AccuracyScoreController(AccuracyQueryService accuracyQueryService) {
        this.accuracyQueryService = accuracyQueryService;
    }

    @GetMapping("/summary")
    public ResponseEntity<List<ProviderScoreDto>> getRankedProviderSummary(
            @RequestParam String city,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Integer horizon,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        List<ProviderScoreDto> summary = accuracyQueryService.getRankedProviderSummary(city, days, horizon, targetDate);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/details")
    public ResponseEntity<List<AccuracyScoreDto>> getDetailedScores(
            @RequestParam String city,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Integer horizon,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        List<AccuracyScoreDto> details = accuracyQueryService.getDetailedScores(city, days, horizon, targetDate);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/filters")
    public ResponseEntity<FilterOptionsDto> getFilterOptions(@RequestParam String city) {
        FilterOptionsDto options = accuracyQueryService.getFilterOptions(city);
        return ResponseEntity.ok(options);
    }
}