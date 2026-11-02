package org.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderEntity {
    private Long id;
    private String orderCode;
    private String cargoType;
    private BigDecimal cargoTotalWeight;
    private BigDecimal allocatedWeight;
    private BigDecimal pendingWeight;
    private String pickupLocationId;
    private String pickupLocationName;
    private String pickupLocation;
    private String deliveryLocationId;
    private String deliveryLocationName;
    private String deliveryLocation;
    private BigDecimal routeDistanceKm;
    private BigDecimal routeDurationH;
    private BigDecimal revenuePerTonKm;
    private BigDecimal totalRevenue;
    private String status;
    private LocalDateTime createdAt;

    public OrderEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getCargoType() {
        return cargoType;
    }

    public BigDecimal getCargoTotalWeight() {
        return cargoTotalWeight;
    }

    public BigDecimal getAllocatedWeight() {
        return allocatedWeight;
    }

    public BigDecimal getPendingWeight() {
        return pendingWeight;
    }

    public String getPickupLocationId() {
        return pickupLocationId;
    }

    public String getPickupLocationName() {
        return pickupLocationName;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getDeliveryLocationId() {
        return deliveryLocationId;
    }

    public String getDeliveryLocationName() {
        return deliveryLocationName;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public BigDecimal getRouteDistanceKm() {
        return routeDistanceKm;
    }

    public BigDecimal getRouteDurationH() {
        return routeDurationH;
    }

    public BigDecimal getRevenuePerTonKm() {
        return revenuePerTonKm;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public void setCargoTotalWeight(BigDecimal cargoTotalWeight) {
        this.cargoTotalWeight = cargoTotalWeight;
    }

    public void setAllocatedWeight(BigDecimal allocatedWeight) {
        this.allocatedWeight = allocatedWeight;
    }

    public void setPendingWeight(BigDecimal pendingWeight) {
        this.pendingWeight = pendingWeight;
    }

    public void setPickupLocationId(String pickupLocationId) {
        this.pickupLocationId = pickupLocationId;
    }

    public void setPickupLocationName(String pickupLocationName) {
        this.pickupLocationName = pickupLocationName;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public void setDeliveryLocationId(String deliveryLocationId) {
        this.deliveryLocationId = deliveryLocationId;
    }

    public void setDeliveryLocationName(String deliveryLocationName) {
        this.deliveryLocationName = deliveryLocationName;
    }

    public void setDeliveryLocation(String deliveryLocation) {
        this.deliveryLocation = deliveryLocation;
    }

    public void setRouteDistanceKm(BigDecimal routeDistanceKm) {
        this.routeDistanceKm = routeDistanceKm;
    }

    public void setRouteDurationH(BigDecimal routeDurationH) {
        this.routeDurationH = routeDurationH;
    }

    public void setRevenuePerTonKm(BigDecimal revenuePerTonKm) {
        this.revenuePerTonKm = revenuePerTonKm;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}