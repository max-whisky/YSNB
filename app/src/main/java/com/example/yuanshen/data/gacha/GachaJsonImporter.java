package com.YSNB.yuanshen.data.gacha;

import com.YSNB.yuanshen.core.model.GachaImportResult;
import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.data.local.GachaRecordDao;
import com.YSNB.yuanshen.data.local.GachaRecordEntity;
import com.YSNB.yuanshen.data.local.AppDatabase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class GachaJsonImporter {
    private static final int MAX_JSON_CHARS = 20 * 1024 * 1024;
    private static final Pattern NUMERIC_ID = Pattern.compile("[1-9][0-9]{5,24}");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private final GachaRecordDao dao;
    private final AppDatabase database;

    public GachaJsonImporter(GachaRecordDao dao, AppDatabase database) {
        this.dao = dao;
        this.database = database;
    }

    public GachaImportResult importUigf(InputStream input, String expectedUid) throws IOException {
        if (input == null) throw new IOException("无法读取所选文件");
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JSONTokener tokenReader = new JSONTokener(readJson(reader));
            Object rootValue = tokenReader.nextValue();
            if (tokenReader.nextClean() != 0) throw new IOException("记录文件末尾包含多余内容");
            if (!(rootValue instanceof JSONObject)) throw new IOException("记录文件顶层格式不正确");
            JSONObject root = (JSONObject) rootValue;
            JSONObject info = root.optJSONObject("info");
            String fileUid = info == null ? "" : info.optString("uid", "").trim();
            if (!validId(fileUid) || !expectedUid.equals(fileUid)) {
                throw new IOException("文件中的UID与当前角色不一致，已取消导入");
            }
            JSONArray list = root.optJSONArray("list");
            if (list == null) throw new IOException("文件中缺少抽卡记录列表");
            return importRecords(list, expectedUid);
        } catch (org.json.JSONException error) {
            throw new IOException("抽卡记录文件格式损坏", error);
        }
    }

    private static String readJson(InputStreamReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            if (text.length() + read > MAX_JSON_CHARS) {
                throw new IOException("抽卡记录文件过大，最大支持二十兆字节");
            }
            text.append(buffer, 0, read);
        }
        return text.toString();
    }

    private GachaImportResult importRecords(JSONArray values, String expectedUid) throws IOException {
        List<GachaRecord> parsed = new ArrayList<>();
        int invalidCount = 0;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            GachaRecord record = parseRecord(value, expectedUid);
            if (record == null) {
                invalidCount++;
            } else {
                parsed.add(record);
            }
        }

        Map<String, List<GachaRecord>> incomingByTime = groupModelsByTime(parsed);
        final int finalInvalidCount = invalidCount;
        final GachaImportResult[] result = new GachaImportResult[1];
        database.runInTransaction(() -> result[0] = importInTransaction(
                incomingByTime, expectedUid, finalInvalidCount));
        return result[0];
    }

    private GachaImportResult importInTransaction(
            Map<String, List<GachaRecord>> incomingByTime,
            String expectedUid,
            int invalidCount
    ) {
        List<GachaRecordEntity> existing = dao.getForUid(expectedUid);
        Map<String, List<GachaRecordEntity>> existingByTime = groupEntitiesByTime(existing);
        Map<String, GachaRecordEntity> existingById = new HashMap<>();
        for (GachaRecordEntity entity : existing) existingById.put(entity.id, entity);
        List<GachaRecordEntity> additions = new ArrayList<>();
        Set<String> incomingIds = new HashSet<>();
        int duplicateCount = 0;
        int conflictCount = 0;
        for (Map.Entry<String, List<GachaRecord>> entry : incomingByTime.entrySet()) {
            List<GachaRecord> incomingBatch = entry.getValue();
            List<GachaRecordEntity> existingBatch = existingByTime.get(entry.getKey());
            if (existingBatch != null && !existingBatch.isEmpty()) {
                if (sameBatch(incomingBatch, existingBatch)) duplicateCount += incomingBatch.size();
                else conflictCount += incomingBatch.size();
                continue;
            }
            boolean batchConflict = false;
            Set<String> batchIds = new HashSet<>();
            for (GachaRecord record : incomingBatch) {
                if (!batchIds.add(record.getId()) || incomingIds.contains(record.getId())
                        || existingById.containsKey(record.getId())) {
                    batchConflict = true;
                    break;
                }
            }
            if (batchConflict) {
                conflictCount += incomingBatch.size();
                continue;
            }
            incomingIds.addAll(batchIds);
            for (GachaRecord record : incomingBatch) {
                additions.add(GachaRecordEntity.fromModel(record));
            }
        }
        if (!additions.isEmpty()) dao.insertAll(additions);
        return new GachaImportResult(additions.size(), duplicateCount, conflictCount, invalidCount);
    }

    private static GachaRecord parseRecord(JSONObject value, String expectedUid) throws IOException {
        if (value == null) return null;
        String uid = value.optString("uid", "").trim();
        if (!uid.isEmpty() && (!validId(uid) || !expectedUid.equals(uid))) {
            throw new IOException("记录中包含其他UID，已取消导入");
        }
        String id = value.optString("id", "").trim();
        String time = value.optString("time", "").trim();
        String name = value.optString("name", "").trim();
        String itemType = value.optString("item_type", "").trim();
        String gachaType = value.optString("uigf_gacha_type",
                value.optString("gacha_type", "")).trim();
        int rankType;
        try {
            rankType = Integer.parseInt(value.optString("rank_type", ""));
        } catch (NumberFormatException error) {
            return null;
        }
        if (!validId(id) || !validTime(time) || name.isEmpty() || itemType.isEmpty()
                || rankType < 3 || rankType > 5 || GachaPool.fromRecordType(gachaType) == null) {
            return null;
        }
        return new GachaRecord(id, expectedUid, gachaType, name, itemType, rankType, time);
    }

    private static Map<String, List<GachaRecord>> groupModelsByTime(List<GachaRecord> records) {
        Map<String, List<GachaRecord>> result = new HashMap<>();
        for (GachaRecord record : records) {
            result.computeIfAbsent(record.getTime(), ignored -> new ArrayList<>()).add(record);
        }
        return result;
    }

    private static Map<String, List<GachaRecordEntity>> groupEntitiesByTime(
            List<GachaRecordEntity> records
    ) {
        Map<String, List<GachaRecordEntity>> result = new HashMap<>();
        for (GachaRecordEntity record : records) {
            result.computeIfAbsent(record.time, ignored -> new ArrayList<>()).add(record);
        }
        return result;
    }

    private static boolean sameBatch(
            List<GachaRecord> incoming,
            List<GachaRecordEntity> existing
    ) {
        if (incoming.size() != existing.size()) return false;
        Map<String, Integer> incomingItems = new HashMap<>();
        Map<String, Integer> existingItems = new HashMap<>();
        for (GachaRecord record : incoming) increment(incomingItems, signature(record));
        for (GachaRecordEntity record : existing) increment(existingItems, signature(record));
        return incomingItems.equals(existingItems);
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private static String signature(GachaRecord record) {
        return normalizedPool(record.getGachaType()) + '\u0000' + record.getName() + '\u0000'
                + record.getItemType() + '\u0000' + record.getRankType();
    }

    private static String signature(GachaRecordEntity record) {
        return normalizedPool(record.gachaType) + '\u0000' + record.name + '\u0000'
                + record.itemType + '\u0000' + record.rankType;
    }

    private static String normalizedPool(String type) {
        GachaPool pool = GachaPool.fromRecordType(type);
        return pool == null ? "" : pool.getRequestType();
    }

    private static boolean validId(String value) {
        return value != null && NUMERIC_ID.matcher(value).matches();
    }

    private static boolean validTime(String value) {
        try {
            return LocalDateTime.parse(value, TIME_FORMAT).format(TIME_FORMAT).equals(value);
        } catch (DateTimeParseException error) {
            return false;
        }
    }

    private static void checkCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("导入已取消");
    }
}
