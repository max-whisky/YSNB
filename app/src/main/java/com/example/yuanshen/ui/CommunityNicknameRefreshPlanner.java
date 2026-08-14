package com.YSNB.yuanshen.ui;

import com.YSNB.yuanshen.core.model.SavedAccount;
import java.util.ArrayList;
import java.util.List;

final class CommunityNicknameRefreshPlanner {
    private CommunityNicknameRefreshPlanner() {
    }

    static List<String> build(List<SavedAccount> accounts, String priorityAccountId) {
        List<String> result = new ArrayList<>();
        if (priorityAccountId != null && !priorityAccountId.isBlank()) {
            result.add(priorityAccountId);
        }
        for (SavedAccount account : accounts) {
            if (account.getAccountId().equals(priorityAccountId)) continue;
            String nickname = account.getCommunityNickname();
            if (nickname == null || nickname.isBlank()) result.add(account.getAccountId());
        }
        return result;
    }
}
