package com.cabpooling.employeecabpooling.routing;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DistanceCacheService {

    private final Map<String, DistanceResult> cache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;

    public DistanceResult get(double lat1, double lon1, double lat2, double lon2) {
        return cache.get(toKey(lat1, lon1, lat2, lon2));
    }

    public void put(double lat1, double lon1, double lat2, double lon2, DistanceResult result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear(); // Evict on capacity limit
        }
        cache.put(toKey(lat1, lon1, lat2, lon2), result);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private String toKey(double lat1, double lon1, double lat2, double lon2) {
        return String.format("%.5f,%.5f->%.5f,%.5f", lat1, lon1, lat2, lon2);
    }
}
