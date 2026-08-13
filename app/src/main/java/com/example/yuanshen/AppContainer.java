package com.YSNB.yuanshen;

import android.content.Context;
import androidx.room.Room;
import com.YSNB.yuanshen.core.network.DsSigner;
import com.YSNB.yuanshen.core.network.MihoyoApi;
import com.YSNB.yuanshen.core.network.UrlConnectionTransport;
import com.YSNB.yuanshen.data.auth.AndroidCredentialStore;
import com.YSNB.yuanshen.data.auth.AuthRepository;
import com.YSNB.yuanshen.data.gacha.GachaRepository;
import com.YSNB.yuanshen.data.local.AppDatabase;
import java.util.UUID;

public final class AppContainer {
    private final AuthRepository authRepository;
    private final GachaRepository gachaRepository;

    public AppContainer(Context context) {
        AppDatabase database = Room.databaseBuilder(
                context,
                AppDatabase.class,
                "yuanshen-wish.db"
        ).build();
        String qrDeviceId = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
                .getString("qr_device_id", null);
        if (qrDeviceId == null) {
            qrDeviceId = UUID.randomUUID().toString();
            context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
                    .edit()
                    .putString("qr_device_id", qrDeviceId)
                    .apply();
        }
        MihoyoApi api = new MihoyoApi(
                new UrlConnectionTransport(),
                new DsSigner(),
                qrDeviceId
        );
        AndroidCredentialStore credentialStore = new AndroidCredentialStore(context);
        authRepository = new AuthRepository(api, credentialStore);
        gachaRepository = new GachaRepository(
                api, authRepository, database.gachaRecordDao(), database);
    }

    public AuthRepository getAuthRepository() { return authRepository; }
    public GachaRepository getGachaRepository() { return gachaRepository; }
}
