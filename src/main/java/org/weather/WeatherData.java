package org.weather;

public record WeatherData(String json) {

    @Override
    public String toString() {
        return json;
    }
}
