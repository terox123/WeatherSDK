package org.weather;

public record WeatherCacheEntry(WeatherData data, long timeMillis) {
}
