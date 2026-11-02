package org.example.util;

/** Validated great-circle distance, shared by scene generation and route fallback. */
public final class Geo {
    private Geo() {}
    public static double[] coordinate(String value) {
        if (value == null) throw new IllegalArgumentException("缺少地理坐标");
        String[] parts = value.split(",");
        if (parts.length != 2) throw new IllegalArgumentException("坐标格式必须为经度,纬度");
        try {
            double lng = Double.parseDouble(parts[0].trim()), lat = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(lng) || !Double.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 90) throw new IllegalArgumentException("地理坐标超出范围");
            return new double[]{lng, lat};
        } catch (NumberFormatException e) { throw new IllegalArgumentException("地理坐标无效", e); }
    }
    public static double roadEstimate(String from, String to) {
        double[] a = coordinate(from), b = coordinate(to);
        double dlat = Math.toRadians(b[1] - a[1]), dlng = Math.toRadians(b[0] - a[0]);
        double h = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(Math.toRadians(a[1])) * Math.cos(Math.toRadians(b[1])) * Math.pow(Math.sin(dlng / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, h)))) * 1.25;
    }
}
