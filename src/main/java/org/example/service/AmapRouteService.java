package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class AmapRouteService {

    @Value("${amap.webserviceKey}")
    private String webserviceKey;

    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS).callTimeout(6, java.util.concurrent.TimeUnit.SECONDS).build();
    private final java.util.concurrent.Semaphore requests = new java.util.concurrent.Semaphore(2);
    private final Object[] stripes = java.util.stream.IntStream.range(0, 32).mapToObj(i -> new Object()).toArray();
    private record Entry(RouteResult route, long expires) {}
    private final java.util.concurrent.ThreadPoolExecutor enrichment = new java.util.concurrent.ThreadPoolExecutor(2, 2, 0,
            java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "route-enrichment"); thread.setDaemon(true); return thread;
            }, new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    public void enrich(Runnable work) { enrichment.execute(work); }
    @jakarta.annotation.PreDestroy public void shutdown() { enrichment.shutdownNow(); }
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 成功结果缓存十分钟，失败仅缓存十五秒；容量始终不超过128条。
     */
    private final Map<String, Entry> routeCache = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> e) { return size() > 128; }
    });

    public RouteResult fetchRoute(String origin, String destination) {
        org.example.util.Geo.coordinate(origin); org.example.util.Geo.coordinate(destination);
        String key = origin + "->" + destination;
        synchronized (stripes[(key.hashCode() & Integer.MAX_VALUE) % stripes.length]) {
            Entry entry = routeCache.get(key);
            if (entry != null && entry.expires() > System.currentTimeMillis()) return entry.route();
            boolean acquired = false;
            try {
                acquired = requests.tryAcquire(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!acquired) return RouteResult.fail("ROUTE_BUSY", "ROUTE_BUSY", null);
                RouteResult route = requestRoute(origin, destination);
                routeCache.put(key, new Entry(route, System.currentTimeMillis() + (route.isOk() ? 600000 : 15000)));
                return route;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return RouteResult.fail("CANCELLED", "CANCELLED", null); }
            finally { if (acquired) requests.release(); }
        }
    }

    protected RouteResult requestRoute(String origin, String destination) {
        try {
            String url = "https://restapi.amap.com/v5/direction/driving"
                    + "?origin=" + URLEncoder.encode(origin, StandardCharsets.UTF_8)
                    + "&destination=" + URLEncoder.encode(destination, StandardCharsets.UTF_8)
                    + "&show_fields=polyline"
                    + "&key=" + URLEncoder.encode(webserviceKey, StandardCharsets.UTF_8);

            Request req = new Request.Builder().url(url).get().build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return RouteResult.fail("HTTP_ERROR", "HTTP_ERROR", null);
                resp.body().source().request(262145);
                if (resp.body().source().buffer().size() > 262144) return RouteResult.fail("RESPONSE_TOO_LARGE", "RESPONSE_TOO_LARGE", null);
                String body = resp.body().source().buffer().readUtf8();
                JsonNode root = mapper.readTree(body);

                String status = root.path("status").asText();
                if (!"1".equals(status)) {
                    String info = root.path("info").asText();
                    String infocode = root.path("infocode").asText();
                    return RouteResult.fail(info, infocode, null);
                }

                JsonNode paths = root.path("route").path("paths");
                if (paths == null || !paths.isArray() || paths.size() == 0) {
                    return RouteResult.fail("EMPTY_PATH", "EMPTY_PATH", null);
                }

                JsonNode path = paths.get(0);

                BigDecimal distanceKm = new BigDecimal(path.path("distance").asDouble(0) / 1000.0)
                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal durationH = new BigDecimal(path.path("cost").path("duration").asDouble(0) / 3600.0)
                        .setScale(2, RoundingMode.HALF_UP);

                StringBuilder polylineBuilder = new StringBuilder();
                JsonNode steps = path.path("steps");
                if (steps != null && steps.isArray()) {
                    for (JsonNode step : steps) {
                        String polyline = step.path("polyline").asText("");
                        if (!polyline.isEmpty()) {
                            // Never return a partially stitched road: fall back to endpoints if oversized.
                            if (polylineBuilder.length() + polyline.length() + 1 > 20000)
                                return RouteResult.ok(distanceKm, durationH, origin + ";" + destination, null);
                            if (polylineBuilder.length() > 0) polylineBuilder.append(";");
                            polylineBuilder.append(polyline);
                        }
                    }
                }

                RouteResult ok = RouteResult.ok(distanceKm, durationH, polylineBuilder.toString(), null);

                return ok;
            }
        } catch (IOException | RuntimeException e) {
            return RouteResult.fail("NETWORK_ERROR", "NETWORK_ERROR", e.getMessage());
        }
    }

    public int cachedRouteCount() { return routeCache.size(); }

    public void clearCache() {
        routeCache.clear();
    }

    public static class RouteResult {
        private boolean ok;
        private String info;
        private String infocode;
        private BigDecimal distanceKm;
        private BigDecimal durationH;
        private String polyline;
        private String raw;

        public static RouteResult ok(BigDecimal distanceKm, BigDecimal durationH, String polyline, String raw) {
            RouteResult r = new RouteResult();
            r.ok = true;
            r.info = "OK";
            r.infocode = "10000";
            r.distanceKm = distanceKm;
            r.durationH = durationH;
            r.polyline = polyline;
            r.raw = raw;
            return r;
        }

        public static RouteResult fail(String info, String infocode, String raw) {
            RouteResult r = new RouteResult();
            r.ok = false;
            r.info = info;
            r.infocode = infocode;
            r.distanceKm = BigDecimal.ZERO;
            r.durationH = BigDecimal.ZERO;
            r.polyline = "";
            r.raw = raw;
            return r;
        }

        public boolean isOk() {
            return ok;
        }

        public String getInfo() {
            return info;
        }

        public String getInfocode() {
            return infocode;
        }

        public BigDecimal getDistanceKm() {
            return distanceKm;
        }

        public BigDecimal getDurationH() {
            return durationH;
        }

        public String getPolyline() {
            return polyline;
        }

        public String getRaw() {
            return raw;
        }
    }
}
