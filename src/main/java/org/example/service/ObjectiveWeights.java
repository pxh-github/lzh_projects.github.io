package org.example.service;

/** User preferences are stored as entered; normalized only when evaluating a decision. */
public record ObjectiveWeights(double waiting, double emptyDistance, double utilization, double tonnage, double carbon) {
    public ObjectiveWeights {
        double[] values = {waiting, emptyDistance, utilization, tonnage, carbon};
        double sum = 0;
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0 || value > 1_000_000)
                throw new IllegalArgumentException("目标权重须为0～1000000的有限数字");
            sum += value;
        }
        if (sum <= 0) throw new IllegalArgumentException("至少一个目标权重须大于0");
    }

    public static ObjectiveWeights defaults() { return new ObjectiveWeights(.25, .25, .20, .15, .15); }

    public double[] normalized() {
        double sum = waiting + emptyDistance + utilization + tonnage + carbon;
        return new double[]{waiting / sum, emptyDistance / sum, utilization / sum, tonnage / sum, carbon / sum};
    }
}
