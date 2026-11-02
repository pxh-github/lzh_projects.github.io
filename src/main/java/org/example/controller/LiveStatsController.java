package org.example.controller;
import org.example.service.LiveStatsService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@CrossOrigin("*")
@RequestMapping("/stats")
public class LiveStatsController {
    private final LiveStatsService service;
    public LiveStatsController(LiveStatsService service){this.service=service;}
    @GetMapping("/live") public Object live(@RequestParam(defaultValue="120") int limit) {
        if(limit<1||limit>500)throw new IllegalArgumentException("历史采样数量须为1～500");
        Map<String,Object> current=service.capture();
        return Map.of("current",current,"history",service.history((String)current.get("sceneId"),limit),"source","DATABASE","samplingIntervalMs",3000);
    }
    @GetMapping("/history") public Object history(@RequestParam String sceneId,@RequestParam(defaultValue="120") int limit) {
        return service.history(sceneId,limit);
    }
}
