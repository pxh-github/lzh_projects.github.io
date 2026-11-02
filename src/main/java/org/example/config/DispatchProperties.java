package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dispatch")
public class DispatchProperties {

    private double revenuePerTonKm;
    private double cargoLossExpK;
    private double cargoLossBaseCoeff;
    private double loadingCostPerTon;
    private double waitBonusPerHour;
    private double waitBonusCap;
    private boolean schedulerEnabled;

    public double getRevenuePerTonKm() {
        return revenuePerTonKm;
    }

    public void setRevenuePerTonKm(double revenuePerTonKm) {
        this.revenuePerTonKm = revenuePerTonKm;
    }

    public double getCargoLossExpK() {
        return cargoLossExpK;
    }

    public void setCargoLossExpK(double cargoLossExpK) {
        this.cargoLossExpK = cargoLossExpK;
    }

    public double getCargoLossBaseCoeff() {
        return cargoLossBaseCoeff;
    }

    public void setCargoLossBaseCoeff(double cargoLossBaseCoeff) {
        this.cargoLossBaseCoeff = cargoLossBaseCoeff;
    }

    public double getLoadingCostPerTon() {
        return loadingCostPerTon;
    }

    public void setLoadingCostPerTon(double loadingCostPerTon) {
        this.loadingCostPerTon = loadingCostPerTon;
    }

    public double getWaitBonusPerHour() {
        return waitBonusPerHour;
    }

    public void setWaitBonusPerHour(double waitBonusPerHour) {
        this.waitBonusPerHour = waitBonusPerHour;
    }

    public double getWaitBonusCap() {
        return waitBonusCap;
    }

    public void setWaitBonusCap(double waitBonusCap) {
        this.waitBonusCap = waitBonusCap;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }
}