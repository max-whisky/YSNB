package com.YSNB.yuanshen.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.YSNB.yuanshen.core.model.AuthSession;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

public final class AndroidCredentialStore implements CredentialStore {
    private static final String KEY_ALIAS = "yuanshen_auth_key";
    private static final String KEY_SESSION = "encrypted_session";
    private static final String KEY_UID = "selected_uid";
    private static final byte FORMAT_VERSION = 1;
    private final SharedPreferences preferences;

    public AndroidCredentialStore(Context context) {
        preferences = context.getSharedPreferences("secure_auth", Context.MODE_PRIVATE);
    }

    @Override
    public AuthSession load() {
        String encoded = preferences.getString(KEY_SESSION, null);
        if (encoded == null) return null;
        try {
            byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            if (buffer.get() != FORMAT_VERSION) return null;
            int ivLength = buffer.get() & 0xff;
            if (ivLength < 12 || ivLength > buffer.remaining()) return null;
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            JSONObject root = new JSONObject(new String(
                    cipher.doFinal(cipherText), StandardCharsets.UTF_8));
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
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    @Override
    public void save(AuthSession session) {
        try {
            JSONObject cookies = new JSONObject();
            session.getCookies().forEach((name, value) -> {
                try {
                    cookies.put(name, value);
                } catch (Exception impossible) {
                    throw new IllegalStateException(impossible);
                }
            });
            JSONObject root = new JSONObject()
                    .put("accountId", session.getAccountId())
                    .put("stoken", session.getStoken())
                    .put("cookies", cookies);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] cipherText = cipher.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            ByteBuffer payload = ByteBuffer.allocate(2 + iv.length + cipherText.length);
            payload.put(FORMAT_VERSION).put((byte) iv.length).put(iv).put(cipherText);
            preferences.edit().putString(
                    KEY_SESSION,
                    Base64.encodeToString(payload.array(), Base64.NO_WRAP)
            ).apply();
        } catch (Exception error) {
            throw new IllegalStateException("无法安全保存登录信息", error);
        }
    }

    @Override
    public String getSelectedUid() {
        return preferences.getString(KEY_UID, null);
    }

    @Override
    public void setSelectedUid(String uid) {
        preferences.edit().putString(KEY_UID, uid).apply();
    }

    @Override
    public void clear() {
        preferences.edit().remove(KEY_SESSION).remove(KEY_UID).apply();
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
}
