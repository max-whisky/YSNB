package com.YSNB.yuanshen.ui;

import com.YSNB.yuanshen.core.model.GachaPool;

/** Central visual mapping for every wish pool. */
public final class GachaPoolStyle {
    private static final GachaPoolStyle CHARACTER = new GachaPoolStyle(
            "角色", 0xFF6657C8, 0xFFF0EDFF);
    private static final GachaPoolStyle WEAPON = new GachaPoolStyle(
            "武器", 0xFF287C8E, 0xFFE4F4F6);
    private static final GachaPoolStyle STANDARD = new GachaPoolStyle(
            "常驻", 0xFF2B7F9D, 0xFFE7F5F9);
    private static final GachaPoolStyle NOVICE = new GachaPoolStyle(
            "新手", 0xFF39866B, 0xFFE8F6EF);
    private static final GachaPoolStyle CHRONICLED = new GachaPoolStyle(
            "集录", 0xFFB04F72, 0xFFFBEAF0);
    private static final GachaPoolStyle UNKNOWN = new GachaPoolStyle(
            "其他", 0xFF697386, 0xFFF1F3F6);

    private final String shortName;
    private final int accentColor;
    private final int surfaceColor;

    private GachaPoolStyle(String shortName, int accentColor, int surfaceColor) {
        this.shortName = shortName;
        this.accentColor = accentColor;
        this.surfaceColor = surfaceColor;
    }

    public static GachaPoolStyle forPool(GachaPool pool) {
        if (pool == null) return UNKNOWN;
        switch (pool) {
            case CHARACTER_EVENT:
                return CHARACTER;
            case WEAPON_EVENT:
                return WEAPON;
            case STANDARD:
                return STANDARD;
            case NOVICE:
                return NOVICE;
            case CHRONICLED:
                return CHRONICLED;
            default:
                return UNKNOWN;
        }
    }

    public String getShortName() {
        return shortName;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public int getSurfaceColor() {
        return surfaceColor;
    }

    public static int getWeaponAccentColor() {
        return WEAPON.accentColor;
    }
}
