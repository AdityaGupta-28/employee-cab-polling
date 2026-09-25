package com.cabpooling.employeecabpooling.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Base-32 geohash encoder used for spatial bucketing during clustering.
 * Precision 5 ≈ 4.9 km × 4.9 km cells — enough to group nearby employees
 * without an O(N²) all-pairs distance scan across thousands of bookings.
 */
public final class GeohashUtil {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private GeohashUtil() {}

    public static String encode(double latitude, double longitude, int precision) {
        double[] latInterval = {-90.0, 90.0};
        double[] lonInterval = {-180.0, 180.0};
        StringBuilder hash = new StringBuilder();
        boolean isEven = true;
        int bit = 0;
        int ch = 0;

        while (hash.length() < precision) {
            if (isEven) {
                double mid = (lonInterval[0] + lonInterval[1]) / 2.0;
                if (longitude >= mid) {
                    ch |= 1 << (4 - bit);
                    lonInterval[0] = mid;
                } else {
                    lonInterval[1] = mid;
                }
            } else {
                double mid = (latInterval[0] + latInterval[1]) / 2.0;
                if (latitude >= mid) {
                    ch |= 1 << (4 - bit);
                    latInterval[0] = mid;
                } else {
                    latInterval[1] = mid;
                }
            }
            isEven = !isEven;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }

    /**
     * Returns the center cell plus its 8 neighbours (Moore neighbourhood).
     * Used to expand candidate search without scanning the entire booking pool.
     */
    public static List<String> encodeWithNeighbors(double latitude, double longitude, int precision) {
        String center = encode(latitude, longitude, precision);
        List<String> cells = new ArrayList<>();
        cells.add(center);

        // Approximate neighbour offsets (~ half-cell at precision 5 ≈ 2.5 km)
        double latStep = 180.0 / Math.pow(2, (precision * 5 + 1) / 2.0);
        double lonStep = 360.0 / Math.pow(2, (precision * 5) / 2.0);
        double[] dLat = {-latStep, 0, latStep};
        double[] dLon = {-lonStep, 0, lonStep};

        for (double la : dLat) {
            for (double lo : dLon) {
                if (la == 0 && lo == 0) {
                    continue;
                }
                String neighbour = encode(latitude + la, longitude + lo, precision);
                if (!cells.contains(neighbour)) {
                    cells.add(neighbour);
                }
            }
        }
        return cells;
    }
}
