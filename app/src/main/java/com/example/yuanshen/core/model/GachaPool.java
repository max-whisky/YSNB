package com.YSNB.yuanshen.core.model;

public enum GachaPool {
    CHARACTER_EVENT("角色活动祈愿", "301"),
    WEAPON_EVENT("武器活动祈愿", "302"),
    STANDARD("常驻祈愿", "200"),
    NOVICE("初行者推荐祈愿", "100"),
    CHRONICLED("集录祈愿", "500");

    private final String displayName;
    private final String requestType;

    GachaPool(String displayName, String requestType) {
        this.displayName = displayName;
        this.requestType = requestType;
    }

    public String getDisplayName() { return displayName; }
    public String getRequestType() { return requestType; }

    public static GachaPool fromRecordType(String type) {
        if ("301".equals(type) || "400".equals(type)) return CHARACTER_EVENT;
        if ("302".equals(type)) return WEAPON_EVENT;
        if ("200".equals(type)) return STANDARD;
        if ("100".equals(type)) return NOVICE;
        if ("500".equals(type)) return CHRONICLED;
        return null;
    }
}
