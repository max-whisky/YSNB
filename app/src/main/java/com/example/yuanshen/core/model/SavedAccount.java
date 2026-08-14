package com.YSNB.yuanshen.core.model;

import java.util.Objects;

public final class SavedAccount {
    private final String accountId;
    private final String communityNickname;

    public SavedAccount(String accountId, String communityNickname) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.communityNickname = communityNickname;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCommunityNickname() {
        return communityNickname;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SavedAccount)) return false;
        SavedAccount account = (SavedAccount) other;
        return accountId.equals(account.accountId);
    }

    @Override
    public int hashCode() {
        return accountId.hashCode();
    }
}
