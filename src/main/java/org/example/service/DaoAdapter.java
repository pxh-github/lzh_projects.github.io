package org.example.service;

import org.example.dao.Dao;
import org.example.entity.Dsp;
import org.example.entity.LocationPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DaoAdapter {

    private final Dao dao;
    private final Dsp dsp;

    public DaoAdapter(Dao dao, Dsp dsp) {
        this.dao = dao;
        this.dsp = dsp;
    }

    public List<LocationPoint> loadAllLocations() {
        List<Map<String, Object>> rows = dao.resToJsonObject(
                dsp.getUrl(),
                dsp.getUsername(),
                dsp.getPassword(),
                dsp.getTableName()
        );
        List<LocationPoint> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocationPoint p = new LocationPoint();
            p.setId(String.valueOf(row.get("id")));
            p.setName(String.valueOf(row.get("name")));
            p.setType(String.valueOf(row.get("type")));
            p.setLocation(String.valueOf(row.get("location")));
            list.add(p);
        }
        return list;
    }
}