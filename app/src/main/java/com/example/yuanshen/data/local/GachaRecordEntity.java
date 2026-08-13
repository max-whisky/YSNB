package com.YSNB.yuanshen.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import com.YSNB.yuanshen.core.model.GachaRecord;

@Entity(tableName = "gacha_records", primaryKeys = {"uid", "id"})
public final class GachaRecordEntity {
    @NonNull public final String uid;
    @NonNull public final String id;
    @NonNull public final String gachaType;
    @NonNull public final String name;
    @NonNull public final String itemType;
    public final int rankType;
    @NonNull public final String time;

    public GachaRecordEntity(
            @NonNull String uid,
            @NonNull String id,
            @NonNull String gachaType,
            @NonNull String name,
            @NonNull String itemType,
            int rankType,
            @NonNull String time
    ) {
        this.uid = uid;
        this.id = id;
        this.gachaType = gachaType;
        this.name = name;
        this.itemType = itemType;
        this.rankType = rankType;
        this.time = time;
    }

    public static GachaRecordEntity fromModel(GachaRecord record) {
        return new GachaRecordEntity(
                record.getUid(),
                record.getId(),
                record.getGachaType(),
                record.getName(),
                record.getItemType(),
                record.getRankType(),
                record.getTime()
        );
    }

    public GachaRecord toModel() {
        return new GachaRecord(id, uid, gachaType, name, itemType, rankType, time);
    }
}
