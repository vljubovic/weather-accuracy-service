package ba.root.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterOptionsDto {
    private List<Integer> availableHorizons;
    private List<String> availableDates;
}