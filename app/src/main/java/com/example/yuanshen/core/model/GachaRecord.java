package com.YSNB.yuanshen.core.model;

import java.util.Objects;

public final class GachaRecord {
    private final String id;
    private final String uid;
    private final String gachaType;
    private final String name;
    private final String itemType;
    private final int rankType;
    private final String time;

    public GachaRecord(
            String id,
            String uid,
            String gachaType,
            String name,
            String itemType,
            int rankType,
            String time
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.uid = Objects.requireNonNull(uid, "uid");
        this.gachaType = Objects.requireNonNull(gachaType, "gachaType");
        this.name = Objects.requireNonNull(name, "name");
        this.itemType = Objects.requireNonNull(itemType, "itemType");
        this.rankType = rankType;
        this.time = Objects.requireNonNull(time, "time");
    }

    public String getId() { return id; }
    public String getUid() { return uid; }
    public String getGachaType() { return gachaType; }
    public String getName() { return name; }
    public String getItemType() { return itemType; }
    public int getRankType() { return rankType; }
    public String getTime() { return time; }
}
