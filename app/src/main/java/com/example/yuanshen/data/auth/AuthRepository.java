package com.YSNB.yuanshen.data.auth;

import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.GameRole;
import com.YSNB.yuanshen.core.model.QrLogin;
import com.YSNB.yuanshen.core.model.QrLoginStatus;
import com.YSNB.yuanshen.core.model.SavedAccount;
import com.YSNB.yuanshen.core.network.MihoyoApi;
import com.YSNB.yuanshen.core.network.MihoyoCookieParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class AuthRepository {
    private final MihoyoApi api;
    private final CredentialStore credentialStore;

    public AuthRepository(MihoyoApi api, CredentialStore credentialStore) {
        this.api = api;
        this.credentialStore = credentialStore;
    }

    public List<SavedAccount> getSavedAccounts() { return credentialStore.listAccounts(); }

    public AuthSession restoreSession(String accountId) {
        return credentialStore.load(accountId);
    }

    public AuthSession acceptWebCookies(List<String> cookieHeaders) {
        Map<String, String> cookies = MihoyoCookieParser.merge(cookieHeaders);
        return MihoyoCookieParser.extractSession(cookies);
    }

    public QrLogin createQrLogin() throws IOException { return api.createQrLogin(); }

    public QrLoginStatus queryQrLogin(QrLogin login) throws IOException {
        return api.queryQrLogin(login);
    }

    public AuthSession completeQrLogin(String rawAccount) throws IOException {
        return api.exchangeGameToken(rawAccount);
    }

    public void saveSession(AuthSession session) { credentialStore.save(session); }

    public List<GameRole> getRoles(AuthSession session) throws IOException {
        return api.getRoles(session);
    }

    public String getCommunityNickname(String accountId) throws IOException {
        return api.getCommunityNickname(accountId);
    }

    public String generateAuthKey(AuthSession session, GameRole role) throws IOException {
        return api.generateAuthKey(session, role);
    }

    public String getActiveAccountId() { return credentialStore.getActiveAccountId(); }
    public void setActiveAccountId(String accountId) {
        credentialStore.setActiveAccountId(accountId);
    }
    public String getSelectedUid(String accountId) {
        return credentialStore.getSelectedUid(accountId);
    }
    public void setSelectedUid(String accountId, String uid) {
        credentialStore.setSelectedUid(accountId, uid);
    }
    public void setCommunityNickname(String accountId, String communityNickname) {
        credentialStore.setCommunityNickname(accountId, communityNickname);
    }
    public boolean isManualLoginRequired() { return credentialStore.isManualLoginRequired(); }
    public void setManualLoginRequired(boolean required) {
        credentialStore.setManualLoginRequired(required);
    }
    public void removeAccount(String accountId) { credentialStore.remove(accountId); }
}
