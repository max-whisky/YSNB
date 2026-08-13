package com.YSNB.yuanshen.data.auth;

import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.GameRole;
import com.YSNB.yuanshen.core.model.QrLogin;
import com.YSNB.yuanshen.core.model.QrLoginStatus;
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

    public AuthSession restoreSession() { return credentialStore.load(); }

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

    public String generateAuthKey(AuthSession session, GameRole role) throws IOException {
        return api.generateAuthKey(session, role);
    }

    public String getSelectedUid() { return credentialStore.getSelectedUid(); }
    public void setSelectedUid(String uid) { credentialStore.setSelectedUid(uid); }
    public void logout() { credentialStore.clear(); }
}
