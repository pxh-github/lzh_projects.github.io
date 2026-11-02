package org.example.entity;

public class LocationPoint {
    private String id;
    private String name;
    private String type;
    private String location;

    public LocationPoint() {
    }

    public LocationPoint(String id, String name, String type, String location) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
    }

    public double lng() {
        return Double.parseDouble(location.split(",")[0]);
    }

    public double lat() {
        return Double.parseDouble(location.split(",")[1]);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}