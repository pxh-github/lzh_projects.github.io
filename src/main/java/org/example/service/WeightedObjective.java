package org.example.service;

import java.util.*;

/** Min-max bounds belong to one candidate pool, never to historical runs. Lower is better. */
public final class WeightedObjective {
    private WeightedObjective() {}
    public static final List<String> KEYS = List.of("waiting", "emptyDistance", "utilization", "tonnage", "carbon");

    public record Values(double waiting, double emptyDistance, double utilization, double tonnage, double carbon) {
        public Values {
            for (double value : new double[]{waiting, emptyDistance, utilization, tonnage, carbon})
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("目标值须为非负有限数字");
            if (utilization > 1) throw new IllegalArgumentException("载重里程利用率须为0～1");
        }
        double[] losses() { return new double[]{waiting, emptyDistance, 1 - utilization, -tonnage, carbon}; }
    }

    public record Evaluation(double score, List<Double> normalizedLosses, List<Double> contributions) {}

    public static final class Scale {
        private final double[] minima = new double[5], maxima = new double[5];
        public Scale(List<Values> candidates) {
            if (candidates.isEmpty()) throw new IllegalArgumentException("缺少可行调度候选");
            Arrays.fill(minima, Double.POSITIVE_INFINITY);
            Arrays.fill(maxima, Double.NEGATIVE_INFINITY);
            for (Values candidate : candidates) {
                double[] values = candidate.losses();
                for (int i = 0; i < 5; i++) {
                    minima[i] = Math.min(minima[i], values[i]);
                    maxima[i] = Math.max(maxima[i], values[i]);
                }
            }
        }
        public Evaluation evaluate(Values candidate, ObjectiveWeights weights) {
            double[] values = candidate.losses(), normalizedWeights = weights.normalized();
            List<Double> normalized = new ArrayList<>(), contributions = new ArrayList<>();
            double score = 0;
            for (int i = 0; i < 5; i++) {
                double range = maxima[i] - minima[i];
                double loss = range <= 1e-12 ? 0 : (values[i] - minima[i]) / range;
                double contribution = loss * normalizedWeights[i];
                normalized.add(loss); contributions.add(contribution); score += contribution;
            }
            return new Evaluation(score, List.copyOf(normalized), List.copyOf(contributions));
        }
        public List<Double> minima() { return Arrays.stream(minima).boxed().toList(); }
        public List<Double> maxima() { return Arrays.stream(maxima).boxed().toList(); }
    }

    public record Decision(long vehicleId, String simulationTime, List<Long> orderIds, int candidateCount,
            Values objectives, Evaluation evaluation, List<Double> minima, List<Double> maxima) {}
}
