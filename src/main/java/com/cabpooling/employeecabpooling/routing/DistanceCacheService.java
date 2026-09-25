package com.cabpooling.employeecabpooling.routing;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory LRU distance cache — avoids recomputing Haversine/OSRM for the same
 * coordinate pairs during NN + 2-opt iterations. Evicts eldest entries when full.
 */
@Service
public class DistanceCacheService {

    private static final int MAX_CACHE_SIZE = 10_000;

    private final Map<String, DistanceResult> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, DistanceResult> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    public DistanceResult get(double lat1, double lon1, double lat2, double lon2) {
        return cache.get(toKey(lat1, lon1, lat2, lon2));
    }

    public void put(double lat1, double lon1, double lat2, double lon2, DistanceResult result) {
        cache.put(toKey(lat1, lon1, lat2, lon2), result);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private String toKey(double lat1, double lon1, double lat2, double lon2) {
        // Directional key — road distances (OSRM) are not always symmetric.
        return String.format("%.5f,%.5f->%.5f,%.5f", lat1, lon1, lat2, lon2);
    }
}
