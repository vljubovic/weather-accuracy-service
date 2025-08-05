package ba.root.weather.controller;

import ba.root.weather.dto.AccuracyScoreDto;
import ba.root.weather.dto.FilterOptionsDto;
import ba.root.weather.dto.ProviderScoreDto;
import ba.root.weather.service.AccuracyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accuracy")
@Tag(name = "Accuracy Scores", description = "Endpoints for querying and analyzing forecast accuracy scores")
public class AccuracyScoreController {

    private final AccuracyQueryService accuracyQueryService;

    @Autowired
    public AccuracyScoreController(AccuracyQueryService accuracyQueryService) {
        this.accuracyQueryService = accuracyQueryService;
    }

    @Operation(summary = "Get Ranked Provider Summary",
            description = "Returns aggregated and ranked scores for each provider based on filter criteria. Use this for the main dashboard view.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the summary"),
            @ApiResponse(responseCode = "400", description = "Invalid input parameters")
    })
    @GetMapping("/summary")
    public ResponseEntity<List<ProviderScoreDto>> getRankedProviderSummary(
            @Parameter(description = "The city to query for.", required = true, example = "Sarajevo")
            @RequestParam String city,
            @Parameter(description = "The number of recent days to include in the calculation.", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "Optional filter for a specific forecast horizon (in hours).", example = "24")
            @RequestParam(required = false) Integer horizon,
            @Parameter(description = "Optional filter for a single target date.", example = "2025-08-04")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        List<ProviderScoreDto> summary = accuracyQueryService.getRankedProviderSummary(city, days, horizon, targetDate);
        return ResponseEntity.ok(summary);
    }

    @Operation(summary = "Get Detailed Accuracy Scores",
            description = "Returns a detailed list of all individual accuracy scores for the tabular view.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the detailed scores"),
            @ApiResponse(responseCode = "400", description = "Invalid input parameters")
    })
    @GetMapping("/details")
    public ResponseEntity<List<AccuracyScoreDto>> getDetailedScores(
            @Parameter(description = "The city to query for.", required = true, example = "Sarajevo")
            @RequestParam String city,
            @Parameter(description = "The number of recent days to include in the calculation.", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "Optional filter for a specific forecast horizon (in hours).", example = "48")
            @RequestParam(required = false) Integer horizon,
            @Parameter(description = "Optional filter for a single target date.", example = "2025-08-03")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        List<AccuracyScoreDto> details = accuracyQueryService.getDetailedScores(city, days, horizon, targetDate);
        return ResponseEntity.ok(details);
    }

    @Operation(summary = "Get Available Filter Options",
            description = "Returns a list of distinct dates and horizons available in the database for a given city to populate UI dropdowns.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the filter options")
    })
    @GetMapping("/filters")
    public ResponseEntity<FilterOptionsDto> getFilterOptions(
            @Parameter(description = "The city for which to find available filters.", required = true, example = "Sarajevo")
            @RequestParam String city) {
        FilterOptionsDto options = accuracyQueryService.getFilterOptions(city);
        return ResponseEntity.ok(options);
    }
}