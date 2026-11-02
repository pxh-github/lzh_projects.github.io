package org.example.dto;

import java.math.BigDecimal;

public class DispatchStatsVO {
    private BigDecimal totalWaitTime;
    private BigDecimal completedTonnage;
    private BigDecimal totalCarbonEmission;
    public BigDecimal getTotalWaitTime() { return totalWaitTime; }
    public void setTotalWaitTime(BigDecimal value) { totalWaitTime = value; }
    public BigDecimal getCompletedTonnage() { return completedTonnage; }
    public void setCompletedTonnage(BigDecimal value) { completedTonnage = value; }
    public BigDecimal getTotalCarbonEmission() { return totalCarbonEmission; }
    public void setTotalCarbonEmission(BigDecimal value) { totalCarbonEmission = value; }
    private Integer vehicleCount;
    private Integer idleVehicleCount;
    private Integer orderCount;
    private Integer doneOrderCount;
    private Integer taskCount;
    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;
    private BigDecimal totalTaskHours;
    private BigDecimal profitPerHour;
    private BigDecimal totalPendingWeight;
    private BigDecimal totalEmptyMileageKm;
    private BigDecimal totalLoadedMileageKm;
    private BigDecimal emptyMileageRate;
    private BigDecimal avgLoadUtilization;
    private BigDecimal totalCargoWaitHours;

    public DispatchStatsVO() {
        this.vehicleCount = 0;
        this.idleVehicleCount = 0;
        this.orderCount = 0;
        this.doneOrderCount = 0;
        this.taskCount = 0;
        this.totalRevenue = BigDecimal.ZERO;
        this.totalCost = BigDecimal.ZERO;
        this.totalProfit = BigDecimal.ZERO;
        this.totalTaskHours = BigDecimal.ZERO;
        this.profitPerHour = BigDecimal.ZERO;
        this.totalPendingWeight = BigDecimal.ZERO;
        this.totalEmptyMileageKm = BigDecimal.ZERO;
        this.totalLoadedMileageKm = BigDecimal.ZERO;
        this.emptyMileageRate = BigDecimal.ZERO;
        this.avgLoadUtilization = BigDecimal.ZERO;
        this.totalCargoWaitHours = BigDecimal.ZERO;
    }

    public Integer getVehicleCount() { return vehicleCount; }
    public void setVehicleCount(Integer vehicleCount) { this.vehicleCount = vehicleCount; }
    public Integer getIdleVehicleCount() { return idleVehicleCount; }
    public void setIdleVehicleCount(Integer idleVehicleCount) { this.idleVehicleCount = idleVehicleCount; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getDoneOrderCount() { return doneOrderCount; }
    public void setDoneOrderCount(Integer doneOrderCount) { this.doneOrderCount = doneOrderCount; }
    public Integer getTaskCount() { return taskCount; }
    public void setTaskCount(Integer taskCount) { this.taskCount = taskCount; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalProfit() { return totalProfit; }
    public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }
    public BigDecimal getTotalTaskHours() { return totalTaskHours; }
    public void setTotalTaskHours(BigDecimal totalTaskHours) { this.totalTaskHours = totalTaskHours; }
    public BigDecimal getProfitPerHour() { return profitPerHour; }
    public void setProfitPerHour(BigDecimal profitPerHour) { this.profitPerHour = profitPerHour; }
    public BigDecimal getTotalPendingWeight() { return totalPendingWeight; }
    public void setTotalPendingWeight(BigDecimal totalPendingWeight) { this.totalPendingWeight = totalPendingWeight; }
    public BigDecimal getTotalEmptyMileageKm() { return totalEmptyMileageKm; }
    public void setTotalEmptyMileageKm(BigDecimal totalEmptyMileageKm) { this.totalEmptyMileageKm = totalEmptyMileageKm; }
    public BigDecimal getTotalLoadedMileageKm() { return totalLoadedMileageKm; }
    public void setTotalLoadedMileageKm(BigDecimal totalLoadedMileageKm) { this.totalLoadedMileageKm = totalLoadedMileageKm; }
    public BigDecimal getEmptyMileageRate() { return emptyMileageRate; }
    public void setEmptyMileageRate(BigDecimal emptyMileageRate) { this.emptyMileageRate = emptyMileageRate; }
    public BigDecimal getAvgLoadUtilization() { return avgLoadUtilization; }
    public void setAvgLoadUtilization(BigDecimal avgLoadUtilization) { this.avgLoadUtilization = avgLoadUtilization; }
    public BigDecimal getTotalCargoWaitHours() { return totalCargoWaitHours; }
    public void setTotalCargoWaitHours(BigDecimal totalCargoWaitHours) { this.totalCargoWaitHours = totalCargoWaitHours; }
}
