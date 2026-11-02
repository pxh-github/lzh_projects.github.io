package org.example.dto;

import java.math.BigDecimal;

public class VehicleVO {
    private Long id;
    private String vehicleCode;
    private String vehicleType;
    private BigDecimal maxLoadTon;
    private BigDecimal actualSpeedKmh;
    private BigDecimal simSpeedKms;
    private String currentLocationName;
    private String currentLocation;
    private String status;
    private BigDecimal totalMileageKm;
    private BigDecimal totalProfit;

    public VehicleVO() {
    }

    public Long getId() {
        return id;
    }

    public String getVehicleCode() {
        return vehicleCode;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public BigDecimal getMaxLoadTon() {
        return maxLoadTon;
    }

    public BigDecimal getActualSpeedKmh() {
        return actualSpeedKmh;
    }

    public BigDecimal getSimSpeedKms() {
        return simSpeedKms;
    }

    public String getCurrentLocationName() {
        return currentLocationName;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotalMileageKm() {
        return totalMileageKm;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setVehicleCode(String vehicleCode) {
        this.vehicleCode = vehicleCode;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public void setMaxLoadTon(BigDecimal maxLoadTon) {
        this.maxLoadTon = maxLoadTon;
    }

    public void setActualSpeedKmh(BigDecimal actualSpeedKmh) {
        this.actualSpeedKmh = actualSpeedKmh;
    }

    public void setSimSpeedKms(BigDecimal simSpeedKms) {
        this.simSpeedKms = simSpeedKms;
    }

    public void setCurrentLocationName(String currentLocationName) {
        this.currentLocationName = currentLocationName;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTotalMileageKm(BigDecimal totalMileageKm) {
        this.totalMileageKm = totalMileageKm;
    }

    public void setTotalProfit(BigDecimal totalProfit) {
        this.totalProfit = totalProfit;
    }
}