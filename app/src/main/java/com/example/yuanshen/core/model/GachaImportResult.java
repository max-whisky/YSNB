package com.YSNB.yuanshen.core.model;

public final class GachaImportResult {
    private final int importedCount;
    private final int duplicateCount;
    private final int conflictCount;
    private final int invalidCount;

    public GachaImportResult(
            int importedCount,
            int duplicateCount,
            int conflictCount,
            int invalidCount
    ) {
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
        this.conflictCount = conflictCount;
        this.invalidCount = invalidCount;
    }

    public int getImportedCount() { return importedCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public int getConflictCount() { return conflictCount; }
    public int getInvalidCount() { return invalidCount; }

    public String toDisplayText() {
        return "导入完成：新增 " + importedCount + " 条，重复跳过 " + duplicateCount
                + " 条，冲突跳过 " + conflictCount + " 条，格式错误 " + invalidCount + " 条";
    }
}
