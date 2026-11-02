package org.example.controller;

import org.example.service.CycleScheduler;
import org.example.service.OrderService;
import org.example.service.StatsService;
import org.example.service.VehicleService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;
    private final CycleScheduler cycleScheduler;
    private final org.example.service.Database database;
    private final VehicleService vehicleService;
    private final OrderService orderService;

    public StatsController(org.example.service.Database database, StatsService statsService,
                           CycleScheduler cycleScheduler,
                           VehicleService vehicleService,
                           OrderService orderService) {
        this.statsService = statsService;
        this.cycleScheduler = cycleScheduler;
        this.database = database;
        this.vehicleService = vehicleService;
        this.orderService = orderService;
    }

    @GetMapping("/overview")
    public Object overview() {
        return statsService.overview();
    }

    @GetMapping("/vehicle-totals")
    public Object vehicleTotals() { return statsService.vehicleTotals(); }

    @GetMapping("/compare")
    public Object compareCurrent() {
        return statsService.compareCurrentModes();
    }

    @PostMapping("/compare")
    public Object compareWithReset(@RequestParam(defaultValue = "10") int vehicles,
                                   @RequestParam(defaultValue = "20") int orders) {
        cycleScheduler.reset(() -> database.write(() -> { vehicleService.initVehicles(vehicles); orderService.generateOrders(orders); }));
        return Map.of(
                "ok", true,
                "vehicles", vehicles,
                "orders", orders,
                "compare", statsService.compareCurrentModes()
        );
    }
}
