package com.YSNB.yuanshen.data.auth;

import com.YSNB.yuanshen.core.model.AuthSession;

public interface CredentialStore {
    AuthSession load();
    void save(AuthSession session);
    String getSelectedUid();
    void setSelectedUid(String uid);
    void clear();
}
