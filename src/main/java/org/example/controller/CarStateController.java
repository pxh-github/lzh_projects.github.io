package org.example.controller;

import org.example.dao.Dao;
import org.example.entity.Dsp;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/carState")
public class CarStateController {

    private final Dao dao;
    private final Dsp dsp;

    public CarStateController(Dao dao, Dsp dsp) {
        this.dao = dao;
        this.dsp = dsp;
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> payload) {
        dao.upsertCarState(dsp.getUrl(), dsp.getUsername(), dsp.getPassword(), payload);
        return Map.of("ok", true);
    }

    @DeleteMapping("/{carId}")
    public Map<String, Object> delete(@PathVariable int carId) {
        dao.deleteCarState(dsp.getUrl(), dsp.getUsername(), dsp.getPassword(), carId);
        return Map.of("ok", true);
    }
    @PostMapping("/clear")
    public Map<String, Object> clear() {
        dao.clearCarStateTable(dsp.getUrl(), dsp.getUsername(), dsp.getPassword());
        return Map.of("ok", true);
    }
}
