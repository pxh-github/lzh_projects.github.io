package org.example.controller;

import org.example.service.VehicleService;
import org.example.service.CycleScheduler;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final CycleScheduler cycleScheduler;

    public VehicleController(VehicleService vehicleService,
                             CycleScheduler cycleScheduler) {
        this.vehicleService = vehicleService;
        this.cycleScheduler = cycleScheduler;
    }

    @PostMapping("/init")
    public Map<String, Object> init(@RequestParam(defaultValue = "10") int count) {
        cycleScheduler.reset(() -> vehicleService.initVehicles(count));
        return Map.of("ok", true, "count", count);
    }

    @PostMapping("/add")
    public Map<String, Object> add() {
        vehicleService.addVehicle(cycleScheduler.currentTime());
        return Map.of("ok", true);
    }

    @GetMapping
    public Object list() {
        return vehicleService.listVehicles();
    }
}
