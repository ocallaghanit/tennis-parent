package com.tennis.prediction.util;

import java.util.List;

/**
 * Utility class for generating SVG sparkline points from rating data.
 */
public class SparklineHelper {

    /**
     * Generate SVG polyline points string from a list of rating values.
     * 
     * @param data   List of rating values
     * @param width  SVG viewBox width
     * @param height SVG viewBox height
     * @return Points string for SVG polyline (e.g., "0,10 15,8 30,12 45,5 60,3")
     */
    public static String generateSparklinePoints(List<Double> data, int width, int height) {
        if (data == null || data.isEmpty()) {
            return "0,10 60,10"; // Flat line if no data
        }

        if (data.size() == 1) {
            return "0,10 60,10"; // Flat line for single point
        }

        // Find min/max for scaling
        double min = data.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = data.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        
        // Add small padding to prevent line from touching edges
        double padding = 2;
        double range = max - min;
        if (range == 0) range = 1; // Prevent division by zero
        
        // Calculate points
        StringBuilder points = new StringBuilder();
        int n = data.size();
        
        for (int i = 0; i < n; i++) {
            double x = (i * width) / (double) (n - 1);
            // Invert Y because SVG Y grows downward
            double y = padding + ((max - data.get(i)) / range) * (height - 2 * padding);
            
            if (i > 0) points.append(" ");
            points.append(String.format("%.1f,%.1f", x, y));
        }
        
        return points.toString();
    }
}

