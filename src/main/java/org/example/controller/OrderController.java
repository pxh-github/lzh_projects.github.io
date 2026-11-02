package org.example.controller;

import org.example.service.OrderService;
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
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CycleScheduler cycleScheduler;

    public OrderController(OrderService orderService,
                           CycleScheduler cycleScheduler) {
        this.orderService = orderService;
        this.cycleScheduler = cycleScheduler;
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestParam(defaultValue = "20") int count) {
        cycleScheduler.reset(() -> orderService.generateOrders(count));
        return Map.of("ok", true, "count", count);
    }

    @PostMapping("/add")
    public Map<String, Object> add() {
        orderService.addOrder(cycleScheduler.currentTime());
        return Map.of("ok", true);
    }

    @GetMapping
    public Object list() {
        return orderService.listOrders();
    }
}
