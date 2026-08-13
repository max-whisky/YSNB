package com.YSNB.yuanshen.data.gacha;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.GachaPage;
import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.core.model.GameRole;
import com.YSNB.yuanshen.core.network.MihoyoApi;
import com.YSNB.yuanshen.data.auth.AuthRepository;
import com.YSNB.yuanshen.data.local.GachaRecordDao;
import com.YSNB.yuanshen.data.local.GachaRecordEntity;
import com.YSNB.yuanshen.data.local.AppDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class GachaRepository {
    public interface ProgressListener {
        void onProgress(String poolName, int recordCount);
    }

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGES_PER_POOL = 500;
    private final MihoyoApi api;
    private final AuthRepository authRepository;
    private final GachaRecordDao dao;
    private final GachaJsonImporter jsonImporter;

    public GachaRepository(
            MihoyoApi api,
            AuthRepository authRepository,
            GachaRecordDao dao,
            AppDatabase database
    ) {
        this.api = api;
        this.authRepository = authRepository;
        this.dao = dao;
        this.jsonImporter = new GachaJsonImporter(dao, database);
    }

    public LiveData<List<GachaRecord>> observeRecords(String uid) {
        return Transformations.map(dao.observeForUid(uid), entities -> entities.stream()
                .map(GachaRecordEntity::toModel)
                .collect(Collectors.toList()));
    }

    public com.YSNB.yuanshen.core.model.GachaImportResult importJson(
            InputStream input,
            String uid
    ) throws IOException {
        return jsonImporter.importUigf(input, uid);
    }

    public int sync(AuthSession session, GameRole role, ProgressListener listener)
            throws IOException, InterruptedException {
        String authKey = authRepository.generateAuthKey(session, role);
        int totalRead = 0;
        int totalNew = 0;
        for (GachaPool pool : GachaPool.values()) {
            if (pool == GachaPool.CHRONICLED) continue;
            checkCancelled();
            String endId = "0";
            List<GachaRecordEntity> poolEntities = new ArrayList<>();
            boolean poolFinished = false;
            for (int page = 1; page <= MAX_PAGES_PER_POOL; page++) {
                checkCancelled();
                GachaPage result = api.getGachaPage(
                        authKey, role, pool.getRequestType(), page, endId);
                List<GachaRecord> records = result.getRecords();
                checkCancelled();
                if (records.isEmpty()) {
                    poolFinished = true;
                    break;
                }
                List<String> ids = records.stream().map(GachaRecord::getId).collect(Collectors.toList());
                List<String> existingIds = dao.findExistingIds(role.getUid(), ids);
                for (GachaRecord record : records) {
                    poolEntities.add(GachaRecordEntity.fromModel(record));
                    if (!existingIds.contains(record.getId())) totalNew++;
                }
                totalRead += records.size();
                listener.onProgress(pool.getDisplayName(), totalRead);
                endId = records.get(records.size() - 1).getId();
                if (existingIds.size() == records.size() || records.size() < PAGE_SIZE) {
                    poolFinished = true;
                    break;
                }
                Thread.sleep(300L);
            }
            checkCancelled();
            if (!poolEntities.isEmpty()) dao.upsertAll(poolEntities);
            if (!poolFinished) {
                throw new IOException(pool.getDisplayName() + "达到同步页数上限，记录可能不完整");
            }
        }
        return totalNew;
    }

    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
}
