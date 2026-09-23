package com.cabpooling.employeecabpooling.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Primary
@Component("osrmDistanceProvider")
public class OsrmDistanceProvider implements DistanceProvider {

    private final DistanceProvider fallbackProvider;
    private final DistanceCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.routing.osrm.url:http://router.project-osrm.org/route/v1/driving/}")
    private String osrmBaseUrl;

    @Value("${app.routing.osrm.enabled:true}")
    private boolean osrmEnabled;

    public OsrmDistanceProvider(
            @Qualifier("haversineDistanceProvider") DistanceProvider fallbackProvider,
            DistanceCacheService cacheService,
            ObjectMapper objectMapper) {
        this.fallbackProvider = fallbackProvider;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1200))
                .build();
    }

    @Override
    public DistanceResult calculateDistanceAndDuration(double lat1, double lon1, double lat2, double lon2) {
        DistanceResult cached = cacheService.get(lat1, lon1, lat2, lon2);
        if (cached != null) {
            return cached;
        }

        if (!osrmEnabled) {
            return fallbackProvider.calculateDistanceAndDuration(lat1, lon1, lat2, lon2);
        }

        try {
            // OSRM expects coordinates in lon,lat order
            String uriString = String.format("%s%.6f,%.6f;%.6f,%.6f?overview=false",
                    osrmBaseUrl.endsWith("/") ? osrmBaseUrl : osrmBaseUrl + "/",
                    lon1, lat1, lon2, lat2);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uriString))
                    .timeout(Duration.ofMillis(1500))
                    .header("User-Agent", "EmployeeCabPoolingApp/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if ("Ok".equalsIgnoreCase(root.path("code").asText())) {
                    JsonNode route = root.path("routes").get(0);
                    double meters = route.path("distance").asDouble();
                    double seconds = route.path("duration").asDouble();

                    double distanceKm = Math.round((meters / 1000.0) * 100.0) / 100.0;
                    int durationMinutes = Math.max(1, (int) Math.round(seconds / 60.0));

                    DistanceResult result = DistanceResult.builder()
                            .distanceKm(distanceKm)
                            .durationMinutes(durationMinutes)
                            .build();

                    cacheService.put(lat1, lon1, lat2, lon2, result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("OSRM lookup failed ({}), falling back to Haversine provider: {}", e.getClass().getSimpleName(), e.getMessage());
        }

        // Fallback to Haversine
        return fallbackProvider.calculateDistanceAndDuration(lat1, lon1, lat2, lon2);
    }

    @Override
    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        return calculateDistanceAndDuration(lat1, lon1, lat2, lon2).getDistanceKm();
    }

    @Override
    public int calculateDurationMinutes(double lat1, double lon1, double lat2, double lon2) {
        return calculateDistanceAndDuration(lat1, lon1, lat2, lon2).getDurationMinutes();
    }

    @Override
    public String getProviderName() {
        return "OSRM_WITH_HAVERSINE_FALLBACK";
    }
}
