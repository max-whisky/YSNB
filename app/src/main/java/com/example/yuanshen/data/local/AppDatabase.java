package com.YSNB.yuanshen.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {GachaRecordEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract GachaRecordDao gachaRecordDao();
}
