package org.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class OpenWeatherSdk {
    private final String apiKey;
    private final Mode mode;
    private final int pollingInterval;
    private final HttpClient http = HttpClient.newBuilder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    // LRU cache limited to 10 entries
    private final Map<String, WeatherCacheEntry> cache = new LinkedHashMap<>(16,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,WeatherCacheEntry> e){
            return size() > 10;
        }
    };
    private final Object lock = new Object();

    private ScheduledExecutorService scheduler;

    // Freshness threshold in seconds (10 minutes)
    private static final long FRESH = 600;
    // Base endpoint
    private static final String API = "https://api.openweathermap.org/data/2.5/weather";

    /**
     * Package-private constructor - enforce creation through SdkFactory.
     */
    OpenWeatherSdk(String key, Mode m, int poll) throws WeatherException {
        this.apiKey = key;
        this.mode = m;
        this.pollingInterval = Math.max(60, poll);
        if (m == Mode.POLLING) startPolling();
    }

    private void startPolling(){
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::refreshAll, 0, pollingInterval, TimeUnit.SECONDS);
    }

    private void refreshAll(){
        List<String> list;
        synchronized(lock){
            list = new ArrayList<>(cache.keySet());
        }
        for (String c : list) {
            try {
                fetchAndStore(c);
            }
            catch (Exception ignore) {
                //polling should not crash
            }
        }
    }


    /**
     * Public method: get weather by city name. Returns JSON string formatted per spec.
     * Checks cache and freshness; if stale or missing, fetches from API.
     */
    public String getWeatherByCity(String city) throws WeatherException {
        if (city == null || city.isBlank())
            throw new WeatherException("City required");
        city = city.trim();
        long now = Instant.now().getEpochSecond();

        synchronized (lock) {
            var e = cache.get(city);
            if (e != null && now - e.timeMillis() < FRESH)
                return e.data().json();
        }
        return fetchAndStore(city);
    }

    private String fetchAndStore(String city) throws WeatherException {
        long now = Instant.now().getEpochSecond();
        String json = fetch(city);
        synchronized (lock) {
            cache.put(city, new WeatherCacheEntry(new WeatherData(json), now));
        }
        return json;
    }

    /**
     * Performs HTTP call to OpenWeatherMap and maps response to the required JSON structure.
     */
    private String fetch(String city) throws WeatherException {
        try {
            String url = API + "?q=" + java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8) + "&appid=" + apiKey;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                // Try to include message if present
                String reason = "HTTP " + res.statusCode();
                try {
                    JsonNode err = mapper.readTree(res.body());
                    if (err.has("message")) reason += ": " + err.get("message").asText();
                } catch (Exception ignored) {}
                throw new WeatherException("API error: " + reason);
            }

            JsonNode r = mapper.readTree(res.body());
            ObjectNode out = mapper.createObjectNode();

            // weather
            if (r.has("weather") && r.get("weather").isArray() && r.get("weather").size() > 0) {
                var w = r.get("weather").get(0);
                var wn = mapper.createObjectNode();
                wn.put("main", w.path("main").asText());
                wn.put("description", w.path("description").asText());
                out.set("weather", wn);
            }

            // temperature
            var tn = mapper.createObjectNode();
            tn.put("temp", r.path("main").path("temp").asDouble());
            tn.put("feels_like", r.path("main").path("feels_like").asDouble());
            out.set("temperature", tn);

            out.put("visibility", r.path("visibility").asInt(0));

            // wind
            var wi = mapper.createObjectNode();
            wi.put("speed", r.path("wind").path("speed").asDouble(0.0));
            out.set("wind", wi);

            out.put("datetime", r.path("dt").asLong(0));

            var sn = mapper.createObjectNode();
            sn.put("sunrise", r.path("sys").path("sunrise").asLong(0));
            sn.put("sunset", r.path("sys").path("sunset").asLong(0));
            out.set("sys", sn);

            out.put("timezone", r.path("timezone").asInt(0));
            out.put("name", r.path("name").asText(city));

            return mapper.writeValueAsString(out);
        } catch (IOException e) {
            throw new WeatherException("I/O error when calling API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherException("Request interrupted", e);
        }
    }

    /**
     * Delete/unregister instance — stops polling and clears cache.
     */
    public void delete(){
        if (scheduler != null) scheduler.shutdownNow();
        synchronized (lock) { cache.clear(); }
        SdkFactory.remove(apiKey);
    }
}
