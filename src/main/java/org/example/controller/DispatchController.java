package org.example.controller;

import org.example.entity.DispatchTask;
import org.example.service.CycleScheduler;
import org.example.service.DispatchService;
import org.example.service.OrderService;
import org.example.service.VehicleService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;
    private final CycleScheduler cycleScheduler;
    private final org.example.service.Database database;
    private final VehicleService vehicleService;
    private final OrderService orderService;

    public DispatchController(org.example.service.Database database, DispatchService dispatchService,
                              CycleScheduler cycleScheduler,
                              VehicleService vehicleService,
                              OrderService orderService) {
        this.dispatchService = dispatchService;
        this.cycleScheduler = cycleScheduler;
        this.database = database;
        this.vehicleService = vehicleService;
        this.orderService = orderService;
    }

    @PostMapping("/reset-all")
    public Object resetAll(@RequestParam(defaultValue = "10") int vehicles,
                           @RequestParam(defaultValue = "20") int orders) {
        cycleScheduler.reset(() -> database.write(() -> { vehicleService.initVehicles(vehicles); orderService.generateOrders(orders); }));
        return Map.of(
                "ok", true,
                "vehicles", vehicles,
                "orders", orders,
                "message", String.format("已重置：%d 辆车，%d 个订单", vehicles, orders)
        );
    }

    @PostMapping("/run-batch")
    public Object runBatch(@RequestParam(defaultValue = "OPTIMIZED") String mode) {
        List<DispatchTask> tasks = dispatchService.runBatchRound(mode);
        return Map.of(
                "ok", true,
                "mode", mode.toUpperCase(),
                "count", tasks.size(),
                "tasks", tasks
        );
    }

    @PostMapping("/cycle/start")
    public Object startCycle(@RequestParam(defaultValue = "OPTIMIZED") String mode) {
        cycleScheduler.start(mode);
        return Map.of(
                "ok", true,
                "running", true,
                "mode", mode.toUpperCase(),
                "message", "循环调度已启动"
        );
    }

    @PostMapping("/cycle/stop")
    public Object stopCycle() {
        cycleScheduler.stop();
        return Map.of(
                "ok", true,
                "running", false,
                "message", "循环调度已停止"
        );
    }

    @GetMapping("/cycle/status")
    public Object cycleStatus() {
        CycleScheduler.CycleStatus s = cycleScheduler.getStatus();
        return Map.of(
                "running", s.isRunning(),
                "mode", s.getMode(),
                "totalTasksGenerated", s.getTotalTasksGenerated(),
                "lastRoundTasks", s.getLastRoundTasks(),
                "simTime", s.getSimTime(),
                "sceneId", cycleScheduler.sceneId()
        );
    }

    @GetMapping("/tasks")
    public Object listTasks(@RequestParam(defaultValue="-1") long afterId, @RequestParam(defaultValue="1000") int limit, @RequestParam(defaultValue="true") boolean includeRoute) {
        return dispatchService.listTasks(afterId,limit,includeRoute);
    }
}
