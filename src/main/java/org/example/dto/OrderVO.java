package org.example.dto;

import java.math.BigDecimal;

public class OrderVO {
    private Long id;
    private String orderCode;
    private String cargoType;
    private BigDecimal cargoTotalWeight;
    private BigDecimal allocatedWeight;
    private BigDecimal pendingWeight;
    private String pickupLocationName;
    private String deliveryLocationName;
    private BigDecimal routeDistanceKm;
    private String status;

    public OrderVO() {
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

    public String getPickupLocationName() {
        return pickupLocationName;
    }

    public String getDeliveryLocationName() {
        return deliveryLocationName;
    }

    public BigDecimal getRouteDistanceKm() {
        return routeDistanceKm;
    }

    public String getStatus() {
        return status;
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

    public void setPickupLocationName(String pickupLocationName) {
        this.pickupLocationName = pickupLocationName;
    }

    public void setDeliveryLocationName(String deliveryLocationName) {
        this.deliveryLocationName = deliveryLocationName;
    }

    public void setRouteDistanceKm(BigDecimal routeDistanceKm) {
        this.routeDistanceKm = routeDistanceKm;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}