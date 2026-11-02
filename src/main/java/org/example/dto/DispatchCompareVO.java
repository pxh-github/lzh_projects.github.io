package org.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DispatchCompareVO {
    private String scenarioMode;
    private Integer vehicleCount;
    private Integer orderCount;
    private LocalDateTime comparedAt;
    private DispatchStatsVO baseline;
    private DispatchStatsVO optimized;
    private String objectiveMetric;
    private Boolean guardApplied;
    private BigDecimal profitDelta;
    private BigDecimal profitDeltaRate;
    private BigDecimal hourlyProfitDelta;
    private BigDecimal hourlyProfitDeltaRate;
    private BigDecimal emptyMileageRateDelta;
    private BigDecimal waitHoursDelta;
    private Integer doneOrderDelta;

    public String getScenarioMode() {
        return scenarioMode;
    }

    public void setScenarioMode(String scenarioMode) {
        this.scenarioMode = scenarioMode;
    }

    public Integer getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(Integer vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public LocalDateTime getComparedAt() {
        return comparedAt;
    }

    public void setComparedAt(LocalDateTime comparedAt) {
        this.comparedAt = comparedAt;
    }

    public DispatchStatsVO getBaseline() {
        return baseline;
    }

    public void setBaseline(DispatchStatsVO baseline) {
        this.baseline = baseline;
    }

    public DispatchStatsVO getOptimized() {
        return optimized;
    }

    public void setOptimized(DispatchStatsVO optimized) {
        this.optimized = optimized;
    }

    public String getObjectiveMetric() {
        return objectiveMetric;
    }

    public void setObjectiveMetric(String objectiveMetric) {
        this.objectiveMetric = objectiveMetric;
    }

    public Boolean getGuardApplied() {
        return guardApplied;
    }

    public void setGuardApplied(Boolean guardApplied) {
        this.guardApplied = guardApplied;
    }

    public BigDecimal getProfitDelta() {
        return profitDelta;
    }

    public void setProfitDelta(BigDecimal profitDelta) {
        this.profitDelta = profitDelta;
    }

    public BigDecimal getProfitDeltaRate() {
        return profitDeltaRate;
    }

    public void setProfitDeltaRate(BigDecimal profitDeltaRate) {
        this.profitDeltaRate = profitDeltaRate;
    }

    public BigDecimal getHourlyProfitDelta() {
        return hourlyProfitDelta;
    }

    public void setHourlyProfitDelta(BigDecimal hourlyProfitDelta) {
        this.hourlyProfitDelta = hourlyProfitDelta;
    }

    public BigDecimal getHourlyProfitDeltaRate() {
        return hourlyProfitDeltaRate;
    }

    public void setHourlyProfitDeltaRate(BigDecimal hourlyProfitDeltaRate) {
        this.hourlyProfitDeltaRate = hourlyProfitDeltaRate;
    }

    public BigDecimal getEmptyMileageRateDelta() {
        return emptyMileageRateDelta;
    }

    public void setEmptyMileageRateDelta(BigDecimal emptyMileageRateDelta) {
        this.emptyMileageRateDelta = emptyMileageRateDelta;
    }

    public BigDecimal getWaitHoursDelta() {
        return waitHoursDelta;
    }

    public void setWaitHoursDelta(BigDecimal waitHoursDelta) {
        this.waitHoursDelta = waitHoursDelta;
    }

    public Integer getDoneOrderDelta() {
        return doneOrderDelta;
    }

    public void setDoneOrderDelta(Integer doneOrderDelta) {
        this.doneOrderDelta = doneOrderDelta;
    }
}
