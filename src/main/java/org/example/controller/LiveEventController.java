package org.example.controller;
import org.example.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @CrossOrigin("*") @RequestMapping("/dispatch/events")
public class LiveEventController {
    private final LiveEventService events; private final CycleScheduler cycle;
    public LiveEventController(LiveEventService events,CycleScheduler cycle){this.events=events;this.cycle=cycle;}
    @GetMapping public Object history(){synchronized(cycle){var time=cycle.currentTime();return Map.of("sceneId",cycle.sceneId(),"simulationTime",time.toString(),"events",events.history(cycle.sceneId(),time),"activeCount",events.activeCount(cycle.sceneId(),time),"source","SIMULATION");}}
    @PostMapping public Object create(@RequestBody LiveEventService.Request request){synchronized(cycle){return events.create(cycle.sceneId(),cycle.currentTime(),request);}}
}
