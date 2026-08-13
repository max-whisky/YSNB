package com.YSNB.yuanshen.core.model;

import java.util.Collections;
import java.util.List;

public final class PoolStatistics {
    private final GachaPool pool;
    private final int totalPulls;
    private final int fiveStarCount;
    private final int fourStarCount;
    private final int currentPity;
    private final Double averageFiveStarPity;
    private final List<GachaRecord> fiveStarRecords;

    public PoolStatistics(
            GachaPool pool,
            int totalPulls,
            int fiveStarCount,
            int fourStarCount,
            int currentPity,
            Double averageFiveStarPity,
            List<GachaRecord> fiveStarRecords
    ) {
        this.pool = pool;
        this.totalPulls = totalPulls;
        this.fiveStarCount = fiveStarCount;
        this.fourStarCount = fourStarCount;
        this.currentPity = currentPity;
        this.averageFiveStarPity = averageFiveStarPity;
        this.fiveStarRecords = Collections.unmodifiableList(fiveStarRecords);
    }

    public GachaPool getPool() { return pool; }
    public int getTotalPulls() { return totalPulls; }
    public int getFiveStarCount() { return fiveStarCount; }
    public int getFourStarCount() { return fourStarCount; }
    public int getCurrentPity() { return currentPity; }
    public Double getAverageFiveStarPity() { return averageFiveStarPity; }
    public List<GachaRecord> getFiveStarRecords() { return fiveStarRecords; }
}
