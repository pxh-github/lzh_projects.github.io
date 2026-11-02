package org.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Vehicle {
    private Long id;
    private String vehicleCode;
    private String vehicleType;
    private BigDecimal maxLoadTon;
    private BigDecimal actualSpeedKmh;
    private BigDecimal simSpeedKms;
    private BigDecimal wearCostPerHour;
    private BigDecimal fuelConsumptionPer100km;
    private BigDecimal fuelCostPerKm;
    private String currentLocationId;
    private String currentLocationName;
    private String currentLocation;
    private String status;
    private LocalDateTime availableAt;
    private BigDecimal totalMileageKm;
    private BigDecimal emptyMileageKm;
    private BigDecimal loadedMileageKm;
    private Integer totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVehicleCode() {
        return vehicleCode;
    }

    public void setVehicleCode(String vehicleCode) {
        this.vehicleCode = vehicleCode;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public BigDecimal getMaxLoadTon() {
        return maxLoadTon;
    }

    public void setMaxLoadTon(BigDecimal maxLoadTon) {
        this.maxLoadTon = maxLoadTon;
    }

    public BigDecimal getActualSpeedKmh() {
        return actualSpeedKmh;
    }

    public void setActualSpeedKmh(BigDecimal actualSpeedKmh) {
        this.actualSpeedKmh = actualSpeedKmh;
    }

    public BigDecimal getSimSpeedKms() {
        return simSpeedKms;
    }

    public void setSimSpeedKms(BigDecimal simSpeedKms) {
        this.simSpeedKms = simSpeedKms;
    }

    public BigDecimal getWearCostPerHour() {
        return wearCostPerHour;
    }

    public void setWearCostPerHour(BigDecimal wearCostPerHour) {
        this.wearCostPerHour = wearCostPerHour;
    }

    public BigDecimal getFuelConsumptionPer100km() {
        return fuelConsumptionPer100km;
    }

    public void setFuelConsumptionPer100km(BigDecimal fuelConsumptionPer100km) {
        this.fuelConsumptionPer100km = fuelConsumptionPer100km;
    }

    public BigDecimal getFuelCostPerKm() {
        return fuelCostPerKm;
    }

    public void setFuelCostPerKm(BigDecimal fuelCostPerKm) {
        this.fuelCostPerKm = fuelCostPerKm;
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public void setCurrentLocationId(String currentLocationId) {
        this.currentLocationId = currentLocationId;
    }

    public String getCurrentLocationName() {
        return currentLocationName;
    }

    public void setCurrentLocationName(String currentLocationName) {
        this.currentLocationName = currentLocationName;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAvailableAt(LocalDateTime availableAt) {
        this.availableAt = availableAt;
    }

    public BigDecimal getTotalMileageKm() {
        return totalMileageKm;
    }

    public void setTotalMileageKm(BigDecimal totalMileageKm) {
        this.totalMileageKm = totalMileageKm;
    }

    public BigDecimal getEmptyMileageKm() {
        return emptyMileageKm;
    }

    public void setEmptyMileageKm(BigDecimal emptyMileageKm) {
        this.emptyMileageKm = emptyMileageKm;
    }

    public BigDecimal getLoadedMileageKm() {
        return loadedMileageKm;
    }

    public void setLoadedMileageKm(BigDecimal loadedMileageKm) {
        this.loadedMileageKm = loadedMileageKm;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(BigDecimal totalProfit) {
        this.totalProfit = totalProfit;
    }
}