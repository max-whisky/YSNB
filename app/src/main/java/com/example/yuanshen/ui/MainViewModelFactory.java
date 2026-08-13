package com.YSNB.yuanshen.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.YSNB.yuanshen.AppContainer;

public final class MainViewModelFactory implements ViewModelProvider.Factory {
    private final AppContainer container;

    public MainViewModelFactory(AppContainer container) {
        this.container = container;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(MainViewModel.class)) {
            throw new IllegalArgumentException("不支持的 ViewModel 类型");
        }
        return (T) new MainViewModel(container);
    }
}
