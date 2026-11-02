package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dispatch")
public class CostConfig {

    private double revenuePerTonKm = 1.50;
    private double cargoLossExpK = 1.05;
    private double cargoLossBaseCoeff = 3.00;
    private double loadingCostPerTon = 8.00;
    private double waitBonusPerHour = 20.00;
    private double waitBonusCap = 300.00;
    private double penaltyPerTonPerHour = 5.00;
    private double optimizedTransportCostFactor = 0.86;
    private double optimizedLoadingCostFactor = 0.90;
    private double optimizedCargoLossCostFactor = 0.88;
    private boolean schedulerEnabled;

    /**
     * Cost1 运输成本：车损(时间×费率) + 油耗(距离×费)
     */
    public double calcCost1(double vehicleWaitTimeH,
                            double pickupTimeH,
                            double transportTimeH,
                            double wearCostPerHour,
                            double pickupDistanceKm,
                            double transportDistanceKm,
                            double fuelCostPerKm) {
        double totalTime = vehicleWaitTimeH + pickupTimeH + transportTimeH;
        double totalDistance = pickupDistanceKm + transportDistanceKm;
        return totalTime * wearCostPerHour + totalDistance * fuelCostPerKm;
    }

    /**
     * Cost2 货物损耗：指数增长，等待越久损耗越大
     */
    public double calcCost2(double cargoWeight, double cargoWaitTimeH) {
        if (!Double.isFinite(cargoWeight) || cargoWeight < 0 || !Double.isFinite(cargoWaitTimeH) || cargoWaitTimeH < 0
                || !Double.isFinite(cargoLossExpK) || cargoLossExpK < 1 || !Double.isFinite(cargoLossBaseCoeff) || cargoLossBaseCoeff < 0)
            throw new IllegalArgumentException("货损参数无效");
        // Loss saturates at the configured full-loss value, rather than growing without bound.
        double exponent = Math.min(50, cargoWaitTimeH * Math.log(cargoLossExpK));
        return cargoWeight * cargoLossBaseCoeff * Math.min(1, Math.expm1(exponent));
    }

    /**
     * Cost3 装卸成本：与重量成正比
     */
    public double calcCost3(double cargoWeight) {
        return cargoWeight * loadingCostPerTon;
    }

    /**
     * Revenue 总收益：重量×路程×单价
     */
    public double calcRevenue(double cargoWeight, double routeDistanceKm) {
        return cargoWeight * routeDistanceKm * revenuePerTonKm;
    }

    /**
     * Score 调度评分 = 净收益 + 等待奖励 + 不接单惩罚避免收益
     */
    public double calcScore(double netProfit, double orderWaitHours, double pendingWeight) {
        orderWaitHours = Math.min(720, Math.max(0, orderWaitHours));
        double waitBonus = Math.min(orderWaitHours * waitBonusPerHour, waitBonusCap);
        double avoidedPenalty = pendingWeight * orderWaitHours * penaltyPerTonPerHour;
        return netProfit + waitBonus + avoidedPenalty;
    }

    /**
     * 不接单惩罚：未分配订单累积的代价
     */
    public double calcNonAcceptPenalty(double pendingWeight, double waitHours) {
        return pendingWeight * waitHours * penaltyPerTonPerHour;
    }

    // ---- getters / setters ----

    public double getRevenuePerTonKm() { return revenuePerTonKm; }
    public void setRevenuePerTonKm(double v) { this.revenuePerTonKm = v; }

    public double getCargoLossExpK() { return cargoLossExpK; }
    public void setCargoLossExpK(double v) { this.cargoLossExpK = v; }

    public double getCargoLossBaseCoeff() { return cargoLossBaseCoeff; }
    public void setCargoLossBaseCoeff(double v) { this.cargoLossBaseCoeff = v; }

    public double getLoadingCostPerTon() { return loadingCostPerTon; }
    public void setLoadingCostPerTon(double v) { this.loadingCostPerTon = v; }

    public double getWaitBonusPerHour() { return waitBonusPerHour; }
    public void setWaitBonusPerHour(double v) { this.waitBonusPerHour = v; }

    public double getWaitBonusCap() { return waitBonusCap; }
    public void setWaitBonusCap(double v) { this.waitBonusCap = v; }

    public double getPenaltyPerTonPerHour() { return penaltyPerTonPerHour; }
    public void setPenaltyPerTonPerHour(double v) { this.penaltyPerTonPerHour = v; }

    public double getOptimizedTransportCostFactor() { return optimizedTransportCostFactor; }
    public void setOptimizedTransportCostFactor(double v) { this.optimizedTransportCostFactor = v; }

    public double getOptimizedLoadingCostFactor() { return optimizedLoadingCostFactor; }
    public void setOptimizedLoadingCostFactor(double v) { this.optimizedLoadingCostFactor = v; }

    public double getOptimizedCargoLossCostFactor() { return optimizedCargoLossCostFactor; }
    public void setOptimizedCargoLossCostFactor(double v) { this.optimizedCargoLossCostFactor = v; }

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean v) { this.schedulerEnabled = v; }
}
