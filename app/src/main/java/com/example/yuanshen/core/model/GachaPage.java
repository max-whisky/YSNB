package com.YSNB.yuanshen.core.model;

import java.util.Collections;
import java.util.List;

public final class GachaPage {
    private final List<GachaRecord> records;

    public GachaPage(List<GachaRecord> records) {
        this.records = Collections.unmodifiableList(records);
    }

    public List<GachaRecord> getRecords() { return records; }
}
