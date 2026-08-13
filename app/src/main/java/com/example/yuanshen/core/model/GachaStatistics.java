package com.YSNB.yuanshen.core.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class GachaStatistics {
    private final Map<GachaPool, PoolStatistics> pools;

    public GachaStatistics(Map<GachaPool, PoolStatistics> pools) {
        this.pools = Collections.unmodifiableMap(new EnumMap<>(pools));
    }

    public PoolStatistics forPool(GachaPool pool) {
        return pools.get(pool);
    }

    public Map<GachaPool, PoolStatistics> getPools() {
        return pools;
    }
}
