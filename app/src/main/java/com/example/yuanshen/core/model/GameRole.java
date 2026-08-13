package com.YSNB.yuanshen.core.model;

import java.util.Objects;

public final class GameRole {
    private final String gameBiz;
    private final String region;
    private final String uid;
    private final String nickname;
    private final int level;
    private final String regionName;

    public GameRole(
            String gameBiz,
            String region,
            String uid,
            String nickname,
            int level,
            String regionName
    ) {
        this.gameBiz = Objects.requireNonNull(gameBiz, "gameBiz");
        this.region = Objects.requireNonNull(region, "region");
        this.uid = Objects.requireNonNull(uid, "uid");
        this.nickname = Objects.requireNonNull(nickname, "nickname");
        this.level = level;
        this.regionName = Objects.requireNonNull(regionName, "regionName");
    }

    public String getGameBiz() { return gameBiz; }
    public String getRegion() { return region; }
    public String getUid() { return uid; }
    public String getNickname() { return nickname; }
    public int getLevel() { return level; }
    public String getRegionName() { return regionName; }
}
