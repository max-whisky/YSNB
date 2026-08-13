package com.YSNB.yuanshen.domain;

import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.core.model.GachaStatistics;
import com.YSNB.yuanshen.core.model.PoolStatistics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GachaStatisticsCalculator {
    private GachaStatisticsCalculator() {
    }

    public static GachaStatistics calculate(List<GachaRecord> records) {
        Map<GachaPool, PoolStatistics> statistics = new EnumMap<>(GachaPool.class);
        for (GachaPool pool : GachaPool.values()) {
            List<GachaRecord> poolRecords = records.stream()
                     .filter(record -> GachaPool.fromRecordType(record.getGachaType()) == pool)
                     .sorted(Comparator.comparing(GachaRecord::getTime)
                             .thenComparing(GachaRecord::getId, GachaStatisticsCalculator::compareIds))
                    .collect(Collectors.toList());
            statistics.put(pool, calculatePool(pool, poolRecords));
        }
        return new GachaStatistics(statistics);
    }

    private static PoolStatistics calculatePool(GachaPool pool, List<GachaRecord> records) {
        List<Integer> fiveStarIndexes = new ArrayList<>();
        List<GachaRecord> fiveStarRecords = new ArrayList<>();
        int fourStarCount = 0;
        for (int index = 0; index < records.size(); index++) {
            GachaRecord record = records.get(index);
            if (record.getRankType() == 5) {
                fiveStarIndexes.add(index);
                fiveStarRecords.add(record);
            } else if (record.getRankType() == 4) {
                fourStarCount++;
            }
        }

        int currentPity = fiveStarIndexes.isEmpty()
                ? records.size()
                : records.size() - fiveStarIndexes.get(fiveStarIndexes.size() - 1) - 1;
        Double average = null;
        if (fiveStarIndexes.size() >= 2) {
            int intervalSum = 0;
            for (int index = 1; index < fiveStarIndexes.size(); index++) {
                intervalSum += fiveStarIndexes.get(index) - fiveStarIndexes.get(index - 1);
            }
            average = (double) intervalSum / (fiveStarIndexes.size() - 1);
        }
        return new PoolStatistics(
                pool,
                records.size(),
                fiveStarIndexes.size(),
                fourStarCount,
                currentPity,
                average,
                fiveStarRecords
        );
    }

    private static int compareIds(String left, String right) {
        if (left.length() != right.length()) return Integer.compare(left.length(), right.length());
        return left.compareTo(right);
    }
}
