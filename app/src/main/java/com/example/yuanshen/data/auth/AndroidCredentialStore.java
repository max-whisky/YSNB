package com.YSNB.yuanshen.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.SavedAccount;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AndroidCredentialStore implements CredentialStore {
    private static final String KEY_ALIAS = "yuanshen_auth_key";
    private static final String KEY_VAULT = "encrypted_accounts";
    private static final String KEY_LEGACY_SESSION = "encrypted_session";
    private static final String KEY_LEGACY_UID = "selected_uid";
    private static final String KEY_MANUAL_LOGIN_REQUIRED = "manual_login_required";
    private static final byte LEGACY_FORMAT_VERSION = 1;
    private static final byte VAULT_FORMAT_VERSION = 2;
    private final SharedPreferences preferences;
    private Vault cachedVault;

    public AndroidCredentialStore(Context context) {
        preferences = context.getSharedPreferences("secure_auth", Context.MODE_PRIVATE);
    }

    @Override
    public synchronized List<SavedAccount> listAccounts() {
        Vault vault = readVault();
        List<SavedAccount> result = new ArrayList<>();
        if (vault.activeAccountId != null) {
            StoredAccount active = vault.accounts.get(vault.activeAccountId);
            result.add(new SavedAccount(vault.activeAccountId, active.communityNickname));
        }
        for (String accountId : vault.accounts.keySet()) {
            if (!accountId.equals(vault.activeAccountId)) {
                result.add(new SavedAccount(
                        accountId, vault.accounts.get(accountId).communityNickname));
            }
        }
        return result;
    }

    @Override
    public synchronized AuthSession load(String accountId) {
        StoredAccount account = readVault().accounts.get(accountId);
        return account == null ? null : account.session;
    }

    @Override
    public synchronized void save(AuthSession session) {
        Vault vault = copyVault(readVault());
        StoredAccount previous = vault.accounts.get(session.getAccountId());
        String selectedUid = previous == null ? null : previous.selectedUid;
        String communityNickname = previous == null ? null : previous.communityNickname;
        vault.accounts.put(session.getAccountId(),
                new StoredAccount(session, selectedUid, communityNickname));
        vault.activeAccountId = session.getAccountId();
        writeVault(vault);
    }

    @Override
    public synchronized String getActiveAccountId() {
        return readVault().activeAccountId;
    }

    @Override
    public synchronized void setActiveAccountId(String accountId) {
        Vault vault = copyVault(readVault());
        if (!vault.accounts.containsKey(accountId)) return;
        vault.activeAccountId = accountId;
        writeVault(vault);
    }

    @Override
    public synchronized String getSelectedUid(String accountId) {
        StoredAccount account = readVault().accounts.get(accountId);
        return account == null ? null : account.selectedUid;
    }

    @Override
    public synchronized void setSelectedUid(String accountId, String uid) {
        Vault vault = copyVault(readVault());
        StoredAccount account = vault.accounts.get(accountId);
        if (account == null) return;
        vault.accounts.put(accountId,
                new StoredAccount(account.session, uid, account.communityNickname));
        writeVault(vault);
    }

    @Override
    public synchronized void setCommunityNickname(
            String accountId, String communityNickname) {
        Vault vault = copyVault(readVault());
        StoredAccount account = vault.accounts.get(accountId);
        if (account == null) return;
        if (communityNickname != null
                && communityNickname.equals(account.communityNickname)) return;
        vault.accounts.put(accountId,
                new StoredAccount(account.session, account.selectedUid, communityNickname));
        writeVault(vault);
    }

    @Override
    public boolean isManualLoginRequired() {
        return preferences.getBoolean(KEY_MANUAL_LOGIN_REQUIRED, false);
    }

    @Override
    public void setManualLoginRequired(boolean required) {
        preferences.edit().putBoolean(KEY_MANUAL_LOGIN_REQUIRED, required).apply();
    }

    @Override
    public synchronized void remove(String accountId) {
        if (accountId == null) return;
        Vault vault = copyVault(readVault());
        if (vault.accounts.remove(accountId) == null) return;
        if (accountId.equals(vault.activeAccountId)) {
            vault.activeAccountId = vault.accounts.isEmpty()
                    ? null : vault.accounts.keySet().iterator().next();
        }
        writeVault(vault);
    }

    private Vault readVault() {
        if (cachedVault != null) return cachedVault;
        String encoded = preferences.getString(KEY_VAULT, null);
        if (encoded == null) {
            cachedVault = migrateLegacyVault();
            return cachedVault;
        }
        try {
            cachedVault = vaultFromJson(decrypt(encoded, VAULT_FORMAT_VERSION));
        } catch (Exception error) {
            clearCredentialData();
            cachedVault = new Vault();
        }
        return cachedVault;
    }

    private Vault migrateLegacyVault() {
        String encoded = preferences.getString(KEY_LEGACY_SESSION, null);
        if (encoded == null) return new Vault();
        AuthSession session;
        try {
            session = sessionFromJson(decrypt(encoded, LEGACY_FORMAT_VERSION));
        } catch (Exception error) {
            clearCredentialData();
            return new Vault();
        }
        String selectedUid = preferences.getString(KEY_LEGACY_UID, null);
        Vault vault = new Vault();
        vault.accounts.put(session.getAccountId(),
                new StoredAccount(session, selectedUid, null));
        vault.activeAccountId = session.getAccountId();
        try {
            writeVault(vault);
        } catch (RuntimeException error) {
            cachedVault = vault;
        }
        return vault;
    }

    private Vault vaultFromJson(JSONObject root) throws Exception {
        Vault vault = new Vault();
        JSONArray accountJson = root.getJSONArray("accounts");
        for (int index = 0; index < accountJson.length(); index++) {
            JSONObject item = accountJson.getJSONObject(index);
            AuthSession session = sessionFromJson(item);
            String selectedUid = item.optString("selectedUid", null);
            String communityNickname = item.optString("communityNickname", null);
            vault.accounts.put(session.getAccountId(),
                    new StoredAccount(session, selectedUid, communityNickname));
        }
        String activeAccountId = root.optString("activeAccountId", null);
        if (activeAccountId != null && vault.accounts.containsKey(activeAccountId)) {
            vault.activeAccountId = activeAccountId;
        } else if (!vault.accounts.isEmpty()) {
            vault.activeAccountId = vault.accounts.keySet().iterator().next();
        }
        return vault;
    }

    private void writeVault(Vault vault) {
        try {
            JSONArray accounts = new JSONArray();
            for (StoredAccount account : vault.accounts.values()) {
                JSONObject item = sessionToJson(account.session);
                if (account.selectedUid != null && !account.selectedUid.isBlank()) {
                    item.put("selectedUid", account.selectedUid);
                }
                if (account.communityNickname != null
                        && !account.communityNickname.isBlank()) {
                    item.put("communityNickname", account.communityNickname);
                }
                accounts.put(item);
            }
            JSONObject root = new JSONObject().put("accounts", accounts);
            if (vault.activeAccountId != null) {
                root.put("activeAccountId", vault.activeAccountId);
            }
            preferences.edit()
                    .putString(KEY_VAULT, encrypt(root, VAULT_FORMAT_VERSION))
                    .remove(KEY_LEGACY_SESSION)
                    .remove(KEY_LEGACY_UID)
                    .apply();
            cachedVault = vault;
        } catch (Exception error) {
            throw new IllegalStateException("无法安全保存登录信息", error);
        }
    }

    private static JSONObject sessionToJson(AuthSession session) throws Exception {
        JSONObject cookies = new JSONObject();
        for (Map.Entry<String, String> cookie : session.getCookies().entrySet()) {
            cookies.put(cookie.getKey(), cookie.getValue());
        }
        return new JSONObject()
                .put("accountId", session.getAccountId())
                .put("stoken", session.getStoken())
                .put("cookies", cookies);
    }

    private static AuthSession sessionFromJson(JSONObject root) throws Exception {
        Map<String, String> cookies = new LinkedHashMap<>();
        JSONObject cookieJson = root.getJSONObject("cookies");
        Iterator<String> keys = cookieJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            cookies.put(key, cookieJson.getString(key));
        }
        return new AuthSession(
                root.getString("accountId"),
                root.getString("stoken"),
                cookies
        );
    }

    private JSONObject decrypt(String encoded, byte expectedVersion) throws Exception {
        byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.remaining() < 2 || buffer.get() != expectedVersion) {
            throw new IllegalStateException("登录信息版本不兼容");
        }
        int ivLength = buffer.get() & 0xff;
        if (ivLength < 12 || ivLength > buffer.remaining()) {
            throw new IllegalStateException("登录信息格式错误");
        }
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        byte[] plainText = cipher.doFinal(cipherText);
        return new JSONObject(new String(plainText, StandardCharsets.UTF_8));
    }

    private String encrypt(JSONObject root, byte version) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] cipherText = cipher.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer payload = ByteBuffer.allocate(2 + iv.length + cipherText.length);
        payload.put(version).put((byte) iv.length).put(iv).put(cipherText);
        return Base64.encodeToString(payload.array(), Base64.NO_WRAP);
    }

    private void clearCredentialData() {
        preferences.edit()
                .remove(KEY_VAULT)
                .remove(KEY_LEGACY_SESSION)
                .remove(KEY_LEGACY_UID)
                .apply();
    }

    private static Vault copyVault(Vault source) {
        Vault copy = new Vault();
        copy.accounts.putAll(source.accounts);
        copy.activeAccountId = source.activeAccountId;
        return copy;
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static final class Vault {
        private final LinkedHashMap<String, StoredAccount> accounts = new LinkedHashMap<>();
        private String activeAccountId;
    }

    private static final class StoredAccount {
        private final AuthSession session;
        private final String selectedUid;
        private final String communityNickname;

        private StoredAccount(
                AuthSession session, String selectedUid, String communityNickname) {
            this.session = session;
            this.selectedUid = selectedUid;
            this.communityNickname = communityNickname;
        }
    }
}
