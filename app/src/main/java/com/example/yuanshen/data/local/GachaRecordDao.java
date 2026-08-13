package com.YSNB.yuanshen.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface GachaRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<GachaRecordEntity> records);

    @Query("SELECT * FROM gacha_records WHERE uid = :uid ORDER BY time DESC, id DESC")
    LiveData<List<GachaRecordEntity>> observeForUid(String uid);

    @Query("SELECT id FROM gacha_records WHERE uid = :uid AND id IN (:ids)")
    List<String> findExistingIds(String uid, List<String> ids);

    @Query("SELECT * FROM gacha_records WHERE uid = :uid")
    List<GachaRecordEntity> getForUid(String uid);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<GachaRecordEntity> records);
}
