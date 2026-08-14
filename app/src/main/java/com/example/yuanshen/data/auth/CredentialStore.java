package com.YSNB.yuanshen.data.auth;

import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.SavedAccount;
import java.util.List;

public interface CredentialStore {
    List<SavedAccount> listAccounts();
    AuthSession load(String accountId);
    void save(AuthSession session);
    String getActiveAccountId();
    void setActiveAccountId(String accountId);
    String getSelectedUid(String accountId);
    void setSelectedUid(String accountId, String uid);
    void setCommunityNickname(String accountId, String communityNickname);
    boolean isManualLoginRequired();
    void setManualLoginRequired(boolean required);
    void remove(String accountId);
}
