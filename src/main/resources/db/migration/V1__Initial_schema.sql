-- Initial schema creation
CREATE TABLE IF NOT EXISTS actual_weather_data (
    id BIGSERIAL PRIMARY KEY,
    city VARCHAR(255) NOT NULL,
    measurement_timestamp TIMESTAMP NOT NULL,
    actual_temperature DOUBLE PRECISION,
    actual_precipitation DOUBLE PRECISION,
    weather VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS forecast_data (
    id BIGSERIAL PRIMARY KEY,
    provider_name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    fetch_timestamp TIMESTAMP NOT NULL,
    target_date DATE NOT NULL,
    predicted_min_temp DOUBLE PRECISION,
    predicted_max_temp DOUBLE PRECISION
);

-- Create indices for better query performance
CREATE INDEX IF NOT EXISTS idx_actual_weather_city ON actual_weather_data(city);
CREATE INDEX IF NOT EXISTS idx_actual_weather_timestamp ON actual_weather_data(measurement_timestamp);
CREATE INDEX IF NOT EXISTS idx_actual_weather_city_timestamp ON actual_weather_data(city, measurement_timestamp);

CREATE INDEX IF NOT EXISTS idx_forecast_city ON forecast_data(city);
CREATE INDEX IF NOT EXISTS idx_forecast_provider ON forecast_data(provider_name);
CREATE INDEX IF NOT EXISTS idx_forecast_target_date ON forecast_data(target_date);
CREATE INDEX IF NOT EXISTS idx_forecast_city_provider ON forecast_data(city, provider_name);
CREATE INDEX IF NOT EXISTS idx_forecast_city_date ON forecast_data(city, target_date);