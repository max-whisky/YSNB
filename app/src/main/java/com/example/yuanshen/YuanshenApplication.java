package com.YSNB.yuanshen;

import android.app.Application;

public final class YuanshenApplication extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer getContainer() {
        return container;
    }
}
