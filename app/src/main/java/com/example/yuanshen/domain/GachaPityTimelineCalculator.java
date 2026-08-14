package com.YSNB.yuanshen.domain;

import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GachaPityTimelineCalculator {
    private static final Set<String> STANDARD_CHARACTER_NAMES = Set.of(
            "琴", "迪卢克", "莫娜", "刻晴", "七七", "提纳里", "迪希雅", "梦见月瑞希");
    private static final Set<String> STANDARD_WEAPON_NAMES = Set.of(
            "风鹰剑", "天空之刃", "狼的末路", "天空之傲", "和璞鸢",
            "天空之脊", "四风原典", "天空之卷", "阿莫斯之弓", "天空之翼");

    private GachaPityTimelineCalculator() {
    }

    public static List<Entry> calculate(GachaPool pool, List<GachaRecord> records) {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(records, "records");

        List<GachaRecord> chronological = new ArrayList<>();
        for (GachaRecord record : records) {
            if (GachaPool.fromRecordType(record.getGachaType()) == pool) {
                chronological.add(record);
            }
        }
        chronological.sort(Comparator.comparing(GachaRecord::getTime)
                .thenComparing(GachaRecord::getId, GachaPityTimelineCalculator::compareIds));

        List<Entry> fiveStars = new ArrayList<>();
        int pullsSinceFiveStar = 0;
        for (GachaRecord record : chronological) {
            pullsSinceFiveStar++;
            if (record.getRankType() >= 5) {
                boolean offBanner = (pool == GachaPool.CHARACTER_EVENT
                        && STANDARD_CHARACTER_NAMES.contains(record.getName()))
                        || (pool == GachaPool.WEAPON_EVENT
                        && STANDARD_WEAPON_NAMES.contains(record.getName()));
                fiveStars.add(Entry.fiveStar(record, pullsSinceFiveStar, offBanner));
                pullsSinceFiveStar = 0;
            }
        }

        List<Entry> timeline = new ArrayList<>();
        timeline.add(Entry.currentPity(pullsSinceFiveStar));
        String previousYear = chronological.isEmpty()
                ? null
                : yearOf(chronological.get(chronological.size() - 1).getTime());
        for (int index = fiveStars.size() - 1; index >= 0; index--) {
            Entry entry = fiveStars.get(index);
            String year = yearOf(entry.getRecord().getTime());
            if (previousYear != null && !previousYear.equals(year)) {
                timeline.add(Entry.yearHeader(year));
            }
            timeline.add(entry);
            previousYear = year;
        }
        return timeline;
    }

    public static int pityCap(GachaPool pool) {
        Objects.requireNonNull(pool, "pool");
        switch (pool) {
            case WEAPON_EVENT:
                return 80;
            case CHARACTER_EVENT:
            case STANDARD:
            case CHRONICLED:
                return 90;
            case NOVICE:
            default:
                return 0;
        }
    }

    public static PullColorTier colorTierFor(int pullCount) {
        if (pullCount < 50) return PullColorTier.GREEN;
        if (pullCount < 75) return PullColorTier.YELLOW;
        return PullColorTier.RED;
    }

    public enum PullColorTier {
        GREEN,
        YELLOW,
        RED
    }

    private static String yearOf(String time) {
        if (time.length() >= 4) return time.substring(0, 4);
        return time;
    }

    private static int compareIds(String left, String right) {
        if (left.length() != right.length()) return Integer.compare(left.length(), right.length());
        return left.compareTo(right);
    }

    public static final class Entry {
        public enum Type {
            CURRENT_PITY,
            YEAR_HEADER,
            FIVE_STAR
        }

        private final Type type;
        private final GachaRecord record;
        private final int pullCount;
        private final String year;
        private final boolean offBanner;

        private Entry(
                Type type,
                GachaRecord record,
                int pullCount,
                String year,
                boolean offBanner
        ) {
            this.type = type;
            this.record = record;
            this.pullCount = pullCount;
            this.year = year;
            this.offBanner = offBanner;
        }

        private static Entry currentPity(int pullCount) {
            return new Entry(Type.CURRENT_PITY, null, pullCount, null, false);
        }

        private static Entry yearHeader(String year) {
            return new Entry(Type.YEAR_HEADER, null, 0, year, false);
        }

        private static Entry fiveStar(
                GachaRecord record,
                int pullCount,
                boolean offBanner
        ) {
            return new Entry(Type.FIVE_STAR, record, pullCount, null, offBanner);
        }

        public Type getType() {
            return type;
        }

        public GachaRecord getRecord() {
            return record;
        }

        public int getPullCount() {
            return pullCount;
        }

        public String getYear() {
            return year;
        }

        public boolean isOffBanner() {
            return offBanner;
        }
    }
}
