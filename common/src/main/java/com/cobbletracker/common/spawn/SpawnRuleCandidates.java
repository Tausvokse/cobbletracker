package com.cobbletracker.common.spawn;

public final class SpawnRuleCandidates<T> {
    private T selected;
    private long count;
    private double maximum;
    private double scaledTotal;
    private double mean;

    public void offer(T candidate, double weight, double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) {
            throw new IllegalArgumentException("roll must be in [0, 1)");
        }
        if (candidate == null || !Double.isFinite(weight) || weight <= 0) return;
        if (weight > maximum) {
            scaledTotal *= maximum / weight;
            maximum = weight;
        }
        double scaled = weight / maximum;
        scaledTotal += scaled;
        if (roll * scaledTotal < scaled) selected = candidate;
        count++;
        mean += (weight - mean) / count;
    }

    public boolean isEmpty() { return count == 0; }
    public T selected() { return selected; }
    public long count() { return count; }
    public double meanWeight() { return mean; }
}
