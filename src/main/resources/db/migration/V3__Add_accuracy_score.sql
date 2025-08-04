
-- Create the accuracy_score table
CREATE TABLE accuracy_score (
                                id BIGSERIAL PRIMARY KEY,
                                provider_name VARCHAR(255) NOT NULL,
                                city VARCHAR(255) NOT NULL,
                                target_date DATE NOT NULL,
                                forecast_horizon INTEGER NOT NULL,
                                min_temp_score DOUBLE PRECISION NOT NULL,
                                max_temp_score DOUBLE PRECISION NOT NULL,
                                precipitation_score VARCHAR(20), -- Store enum as a string (TRUE_POSITIVE, TRUE_NEGATIVE, etc.)

    -- Add a unique constraint to prevent duplicate entries
                                CONSTRAINT unique_accuracy_score UNIQUE (provider_name, city, target_date, forecast_horizon)
);

-- Add indexes for frequently queried fields
CREATE INDEX idx_accuracy_score_provider ON accuracy_score (provider_name);
CREATE INDEX idx_accuracy_score_city ON accuracy_score (city);
CREATE INDEX idx_accuracy_score_target_date ON accuracy_score (target_date);
CREATE INDEX idx_accuracy_score_horizon ON accuracy_score (forecast_horizon);

-- Add comments
COMMENT ON TABLE accuracy_score IS 'Stores accuracy scores for weather forecasts compared to actual weather data';
COMMENT ON COLUMN accuracy_score.provider_name IS 'Name of the weather data provider';
COMMENT ON COLUMN accuracy_score.city IS 'Name of the city for which the forecast was made';
COMMENT ON COLUMN accuracy_score.target_date IS 'The date for which the weather was forecasted';
COMMENT ON COLUMN accuracy_score.forecast_horizon IS 'How many hours in advance the forecast was made';
COMMENT ON COLUMN accuracy_score.min_temp_score IS 'Deviation from actual measured minimum daily temperature';
COMMENT ON COLUMN accuracy_score.max_temp_score IS 'Deviation from actual measured maximum daily temperature';
COMMENT ON COLUMN accuracy_score.precipitation_score IS 'Score for precipitation forecast accuracy (TRUE_POSITIVE, TRUE_NEGATIVE, FALSE_POSITIVE, FALSE_NEGATIVE)';