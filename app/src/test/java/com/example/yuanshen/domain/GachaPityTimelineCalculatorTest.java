package com.YSNB.yuanshen.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class GachaPityTimelineCalculatorTest {
    @Test
    public void calculatesFiveStarIntervalsAndCurrentPityFromNewestFirstRecords() {
        List<GachaRecord> records = Arrays.asList(
                record("7", 3, "2026-02-01 00:00:03"),
                record("6", 5, "2026-02-01 00:00:02"),
                record("5", 3, "2026-02-01 00:00:01"),
                record("4", 4, "2025-12-01 00:00:04"),
                record("3", 5, "2025-12-01 00:00:03"),
                record("2", 3, "2025-12-01 00:00:02"),
                record("1", 3, "2025-12-01 00:00:01")
        );

        List<GachaPityTimelineCalculator.Entry> timeline =
                GachaPityTimelineCalculator.calculate(GachaPool.CHARACTER_EVENT, records);

        assertEquals(GachaPityTimelineCalculator.Entry.Type.CURRENT_PITY, timeline.get(0).getType());
        assertEquals(1, timeline.get(0).getPullCount());
        assertEquals(GachaPityTimelineCalculator.Entry.Type.FIVE_STAR, timeline.get(1).getType());
        assertEquals("6", timeline.get(1).getRecord().getId());
        assertEquals(3, timeline.get(1).getPullCount());
        assertEquals(GachaPityTimelineCalculator.Entry.Type.YEAR_HEADER, timeline.get(2).getType());
        assertEquals("2025", timeline.get(2).getYear());
        assertEquals("3", timeline.get(3).getRecord().getId());
        assertEquals(3, timeline.get(3).getPullCount());
    }

    @Test
    public void ignoresRecordsFromOtherPools() {
        List<GachaRecord> records = Arrays.asList(
                record("3", 3, "2026-01-01 00:00:03"),
                new GachaRecord("2", "uid", "302", "武器", "武器", 5,
                        "2026-01-01 00:00:02"),
                record("1", 5, "2026-01-01 00:00:01")
        );

        List<GachaPityTimelineCalculator.Entry> timeline =
                GachaPityTimelineCalculator.calculate(GachaPool.CHARACTER_EVENT, records);

        assertEquals(2, timeline.size());
        assertEquals(1, timeline.get(0).getPullCount());
        assertEquals(1, timeline.get(1).getPullCount());
    }

    @Test
    public void addsYearHeaderWhenCurrentPityIsNewerThanLatestFiveStar() {
        List<GachaRecord> records = Arrays.asList(
                record("2", 3, "2026-01-01 00:00:01"),
                record("1", 5, "2025-12-31 23:59:59")
        );

        List<GachaPityTimelineCalculator.Entry> timeline =
                GachaPityTimelineCalculator.calculate(GachaPool.CHARACTER_EVENT, records);

        assertEquals(GachaPityTimelineCalculator.Entry.Type.CURRENT_PITY, timeline.get(0).getType());
        assertEquals(1, timeline.get(0).getPullCount());
        assertEquals(GachaPityTimelineCalculator.Entry.Type.YEAR_HEADER, timeline.get(1).getType());
        assertEquals("2025", timeline.get(1).getYear());
        assertEquals(GachaPityTimelineCalculator.Entry.Type.FIVE_STAR, timeline.get(2).getType());
    }

    @Test
    public void usesPoolSpecificPityCaps() {
        assertEquals(90, GachaPityTimelineCalculator.pityCap(GachaPool.CHARACTER_EVENT));
        assertEquals(80, GachaPityTimelineCalculator.pityCap(GachaPool.WEAPON_EVENT));
        assertEquals(90, GachaPityTimelineCalculator.pityCap(GachaPool.STANDARD));
        assertEquals(0, GachaPityTimelineCalculator.pityCap(GachaPool.NOVICE));
        assertEquals(90, GachaPityTimelineCalculator.pityCap(GachaPool.CHRONICLED));
    }

    @Test
    public void usesExactPullCountColorBoundaries() {
        assertEquals(GachaPityTimelineCalculator.PullColorTier.GREEN,
                GachaPityTimelineCalculator.colorTierFor(49));
        assertEquals(GachaPityTimelineCalculator.PullColorTier.YELLOW,
                GachaPityTimelineCalculator.colorTierFor(50));
        assertEquals(GachaPityTimelineCalculator.PullColorTier.YELLOW,
                GachaPityTimelineCalculator.colorTierFor(74));
        assertEquals(GachaPityTimelineCalculator.PullColorTier.RED,
                GachaPityTimelineCalculator.colorTierFor(75));
    }

    @Test
    public void marksEveryStandardCharacterInCharacterEventPoolAsOffBanner() {
        List<String> standardCharacters = Arrays.asList(
                "琴", "迪卢克", "莫娜", "刻晴", "七七", "提纳里", "迪希雅", "梦见月瑞希");

        for (int index = 0; index < standardCharacters.size(); index++) {
            GachaRecord record = namedRecord(
                    String.valueOf(index + 1), "301", standardCharacters.get(index));
            List<GachaPityTimelineCalculator.Entry> timeline =
                    GachaPityTimelineCalculator.calculate(
                            GachaPool.CHARACTER_EVENT, List.of(record));

            assertTrue(standardCharacters.get(index), timeline.get(1).isOffBanner());
        }
    }

    @Test
    public void doesNotMarkLimitedCharacterOrOtherPoolAsOffBanner() {
        GachaRecord limitedCharacter = namedRecord("1", "301", "芙宁娜");
        List<GachaPityTimelineCalculator.Entry> characterTimeline =
                GachaPityTimelineCalculator.calculate(
                        GachaPool.CHARACTER_EVENT, List.of(limitedCharacter));
        assertFalse(characterTimeline.get(1).isOffBanner());

        GachaRecord standardPoolCharacter = namedRecord("2", "200", "琴");
        List<GachaPityTimelineCalculator.Entry> standardTimeline =
                GachaPityTimelineCalculator.calculate(
                        GachaPool.STANDARD, List.of(standardPoolCharacter));
        assertFalse(standardTimeline.get(1).isOffBanner());
    }

    @Test
    public void marksStandardWeaponsOnlyInWeaponEventPoolAsOffBanner() {
        List<String> standardWeapons = Arrays.asList(
                "风鹰剑", "天空之刃", "狼的末路", "天空之傲", "和璞鸢",
                "天空之脊", "四风原典", "天空之卷", "阿莫斯之弓", "天空之翼");

        for (int index = 0; index < standardWeapons.size(); index++) {
            GachaRecord record = new GachaRecord(
                    String.valueOf(index + 1), "uid", "302", standardWeapons.get(index),
                    "武器", 5, "2026-01-01 00:00:00");
            List<GachaPityTimelineCalculator.Entry> timeline =
                    GachaPityTimelineCalculator.calculate(GachaPool.WEAPON_EVENT, List.of(record));
            assertTrue(standardWeapons.get(index), timeline.get(1).isOffBanner());
        }

        GachaRecord limitedWeapon = new GachaRecord(
                "20", "uid", "302", "若水", "武器", 5, "2026-01-01 00:00:00");
        assertFalse(GachaPityTimelineCalculator.calculate(
                GachaPool.WEAPON_EVENT, List.of(limitedWeapon)).get(1).isOffBanner());

        GachaRecord standardPoolWeapon = new GachaRecord(
                "21", "uid", "200", "天空之翼", "武器", 5, "2026-01-01 00:00:00");
        assertFalse(GachaPityTimelineCalculator.calculate(
                GachaPool.STANDARD, List.of(standardPoolWeapon)).get(1).isOffBanner());
    }

    private static GachaRecord record(String id, int rankType, String time) {
        return new GachaRecord(id, "uid", "301", "角色" + id, "角色", rankType, time);
    }

    private static GachaRecord namedRecord(String id, String gachaType, String name) {
        return new GachaRecord(id, "uid", gachaType, name, "角色", 5,
                "2026-01-01 00:00:00");
    }
}
