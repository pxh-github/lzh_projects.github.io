package org.example.entity;

import java.math.BigDecimal;

public class DispatchTask {
    private java.time.LocalDateTime simulationStartedAt;
    private java.time.LocalDateTime simulationFinishedAt;
    public java.time.LocalDateTime getSimulationStartedAt() { return simulationStartedAt; }
    public void setSimulationStartedAt(java.time.LocalDateTime value) { simulationStartedAt = value; }
    public java.time.LocalDateTime getSimulationFinishedAt() { return simulationFinishedAt; }
    public void setSimulationFinishedAt(java.time.LocalDateTime value) { simulationFinishedAt = value; }
    private Long id;
    private String taskCode;
    private Long orderId;
    private Long vehicleId;
    private BigDecimal assignedWeight;
    private BigDecimal pickupDistanceKm;
    private BigDecimal pickupTimeH;
    private BigDecimal transportDistanceKm;
    private BigDecimal transportTimeH;
    private BigDecimal vehicleWaitTimeH;
    private BigDecimal cargoWaitTimeH;
    private BigDecimal cost1;
    private BigDecimal cost2;
    private BigDecimal cost3;
    private BigDecimal totalCost;
    private BigDecimal totalRevenue;
    private BigDecimal netProfit;
    private BigDecimal score;
    private String routePolyline;
    private String status;

    public Long getId() {
        return id;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public BigDecimal getAssignedWeight() {
        return assignedWeight;
    }

    public BigDecimal getPickupDistanceKm() {
        return pickupDistanceKm;
    }

    public BigDecimal getPickupTimeH() {
        return pickupTimeH;
    }

    public BigDecimal getTransportDistanceKm() {
        return transportDistanceKm;
    }

    public BigDecimal getTransportTimeH() {
        return transportTimeH;
    }

    public BigDecimal getVehicleWaitTimeH() {
        return vehicleWaitTimeH;
    }

    public BigDecimal getCargoWaitTimeH() {
        return cargoWaitTimeH;
    }

    public BigDecimal getCost1() {
        return cost1;
    }

    public BigDecimal getCost2() {
        return cost2;
    }

    public BigDecimal getCost3() {
        return cost3;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public BigDecimal getNetProfit() {
        return netProfit;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getRoutePolyline() {
        return routePolyline;
    }

    public String getStatus() {
        return status;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void setAssignedWeight(BigDecimal assignedWeight) {
        this.assignedWeight = assignedWeight;
    }

    public void setPickupDistanceKm(BigDecimal pickupDistanceKm) {
        this.pickupDistanceKm = pickupDistanceKm;
    }

    public void setPickupTimeH(BigDecimal pickupTimeH) {
        this.pickupTimeH = pickupTimeH;
    }

    public void setTransportDistanceKm(BigDecimal transportDistanceKm) {
        this.transportDistanceKm = transportDistanceKm;
    }

    public void setTransportTimeH(BigDecimal transportTimeH) {
        this.transportTimeH = transportTimeH;
    }

    public void setVehicleWaitTimeH(BigDecimal vehicleWaitTimeH) {
        this.vehicleWaitTimeH = vehicleWaitTimeH;
    }

    public void setCargoWaitTimeH(BigDecimal cargoWaitTimeH) {
        this.cargoWaitTimeH = cargoWaitTimeH;
    }

    public void setCost1(BigDecimal cost1) {
        this.cost1 = cost1;
    }

    public void setCost2(BigDecimal cost2) {
        this.cost2 = cost2;
    }

    public void setCost3(BigDecimal cost3) {
        this.cost3 = cost3;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public void setNetProfit(BigDecimal netProfit) {
        this.netProfit = netProfit;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public void setRoutePolyline(String routePolyline) {
        this.routePolyline = routePolyline;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}