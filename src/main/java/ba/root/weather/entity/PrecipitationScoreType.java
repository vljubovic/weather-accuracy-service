package ba.root.weather.entity;

/**
 * Represents the accuracy of precipitation forecast compared to actual weather.
 * TRUE_POSITIVE: Precipitation was forecasted and actually occurred
 * TRUE_NEGATIVE: No precipitation was forecasted and none occurred
 * FALSE_POSITIVE: Precipitation was forecasted but didn't occur
 * FALSE_NEGATIVE: No precipitation was forecasted but it did occur
 */
public enum PrecipitationScoreType {
    TRUE_POSITIVE,
    TRUE_NEGATIVE,
    FALSE_POSITIVE,
    FALSE_NEGATIVE
}