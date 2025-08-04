-- Add predicted_weather column to forecast_data table
ALTER TABLE forecast_data
ADD COLUMN IF NOT EXISTS predicted_weather VARCHAR(50);

-- Create index on the new column
CREATE INDEX IF NOT EXISTS idx_forecast_weather ON forecast_data(predicted_weather);
