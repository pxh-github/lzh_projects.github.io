package org.example.controller;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/amap")
public class AmapDirectionController {

    private final OkHttpClient client = new OkHttpClient();

    @Value("${amap.webserviceKey}")
    private String webserviceKey;

    @GetMapping("/driving")
    public Map<String, Object> driving(@RequestParam String origin,
                                       @RequestParam String destination) throws IOException {

        String url = "https://restapi.amap.com/v5/direction/driving"
                + "?origin=" + URLEncoder.encode(origin, StandardCharsets.UTF_8)
                + "&destination=" + URLEncoder.encode(destination, StandardCharsets.UTF_8)
                + "&show_fields=polyline"
                + "&key=" + URLEncoder.encode(webserviceKey, StandardCharsets.UTF_8);

        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = client.newCall(req).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            return Map.of(
                    "ok", resp.isSuccessful(),
                    "statusCode", resp.code(),
                    "raw", body
            );
        }
    }
}
