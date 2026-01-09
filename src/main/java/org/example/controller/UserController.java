package org.example.controller;

import org.example.entity.Dsp;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import org.example.dao.Dao;

@CrossOrigin("*")
@RestController
@RequestMapping("/user")
public class UserController {
    private final Dao datasourceRead;

    private final Dsp dsp;

    public UserController(Dao datasourceRead, Dsp dsp) {
        this.datasourceRead = datasourceRead;
        this.dsp = dsp;
    }

    @GetMapping()
    public List<Map<String, Object>> getData(){
        return datasourceRead.resToJsonObject(
                dsp.getUrl(),
                dsp.getUsername(),
                dsp.getPassword(),
                dsp.getTableName()
        );
    }
}
