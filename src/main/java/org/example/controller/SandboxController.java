package org.example.controller;
import org.example.sandbox.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@CrossOrigin("*")
@RestController
@RequestMapping("/sandbox")
public class SandboxController {
    private final SandboxService service;
    public SandboxController(SandboxService service) { this.service = service; }
    public record ExperimentRequest(String name, ExperimentConfig config, String copyFrom, long version) {}
    public record RunRequest(String requestKey, String rerunOf) {}
    @GetMapping("/catalog") public Object catalog() { return service.catalog(); }
    @GetMapping("/state") public Object state() { return service.state(); }
    @PostMapping("/experiments") public Object create(@RequestBody ExperimentRequest request) {
        if (request.config() == null) throw new IllegalArgumentException("缺少实验配置");
        return service.create(request.name(),request.config(),request.copyFrom());
    }
    @PutMapping("/experiments/{id}") public Object update(@PathVariable String id, @RequestBody ExperimentRequest request) {
        if (request.config() == null) throw new IllegalArgumentException("缺少实验配置");
        return service.update(id,request.name(),request.config(),request.version());
    }
    @PostMapping("/experiments/{id}/runs") public Object start(@PathVariable String id, @RequestBody RunRequest request) { return service.start(id,request.requestKey(),request.rerunOf()); }
    @GetMapping("/runs/{id}") public Object run(@PathVariable String id) { return service.run(id); }
}
