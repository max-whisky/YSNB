package com.YSNB.yuanshen.ui;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.YSNB.yuanshen.AppContainer;
import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.core.model.GachaStatistics;
import com.YSNB.yuanshen.core.model.GameRole;
import com.YSNB.yuanshen.core.model.QrLogin;
import com.YSNB.yuanshen.core.model.QrLoginStatus;
import com.YSNB.yuanshen.core.model.SavedAccount;
import com.YSNB.yuanshen.data.auth.AuthRepository;
import com.YSNB.yuanshen.data.gacha.GachaRepository;
import com.YSNB.yuanshen.domain.GachaStatisticsCalculator;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.InputStream;

public final class MainViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final GachaRepository gachaRepository;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final ExecutorService nicknameExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AppScreen> screen = new MutableLiveData<>();
    private final MutableLiveData<String> qrUrl = new MutableLiveData<>();
    private final MutableLiveData<String> qrStatus = new MutableLiveData<>("正在生成登录二维码…");
    private final MutableLiveData<List<GameRole>> roles = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<GameRole> selectedRole = new MutableLiveData<>();
    private final MediatorLiveData<List<GachaRecord>> records = new MediatorLiveData<>();
    private final MutableLiveData<GachaStatistics> statistics = new MutableLiveData<>(
            GachaStatisticsCalculator.calculate(Collections.emptyList()));
    private final MutableLiveData<Boolean> syncing = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncStatus = new MutableLiveData<>("尚未同步");
    private final MutableLiveData<Boolean> importing = new MutableLiveData<>(false);
    private final MutableLiveData<List<SavedAccount>> savedAccounts =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private volatile AuthSession session;
    private LiveData<List<GachaRecord>> recordSource;
    private ScheduledFuture<?> qrPolling;
    private Future<?> authTask;
    private Future<?> syncTask;
    private Future<?> importTask;
    private Future<?> nicknameTask;
    private final AtomicInteger authGeneration = new AtomicInteger();
    private final AtomicInteger syncGeneration = new AtomicInteger();
    private final AtomicInteger importGeneration = new AtomicInteger();
    private final AtomicBoolean syncGuard = new AtomicBoolean();
    private final AtomicBoolean importGuard = new AtomicBoolean();

    public MainViewModel(AppContainer container) {
        authRepository = container.getAuthRepository();
        gachaRepository = container.getGachaRepository();
        records.setValue(Collections.emptyList());
        restore();
    }

    public LiveData<AppScreen> getScreen() { return screen; }
    public LiveData<String> getQrUrl() { return qrUrl; }
    public LiveData<String> getQrStatus() { return qrStatus; }
    public LiveData<List<GameRole>> getRoles() { return roles; }
    public LiveData<GameRole> getSelectedRole() { return selectedRole; }
    public LiveData<List<GachaRecord>> getRecords() { return records; }
    public LiveData<GachaStatistics> getStatistics() { return statistics; }
    public LiveData<Boolean> getSyncing() { return syncing; }
    public LiveData<String> getSyncStatus() { return syncStatus; }
    public LiveData<Boolean> getImporting() { return importing; }
    public LiveData<List<SavedAccount>> getSavedAccounts() { return savedAccounts; }
    public LiveData<Event<String>> getMessage() { return message; }

    public void openWebLogin() {
        beginAuthAttempt();
        screen.setValue(AppScreen.WEB_LOGIN);
    }

    public void backToLogin() {
        cancelAuthentication();
        screen.setValue(AppScreen.LOGIN);
    }

    public void resumeSavedLogin(String accountId) {
        int generation = beginAuthAttempt();
        screen.setValue(AppScreen.MAIN);
        authTask = executor.submit(() -> {
            AuthSession restored = authRepository.restoreSession(accountId);
            if (!isAuthCurrent(generation)) return;
            if (restored == null) {
                savedAccounts.postValue(authRepository.getSavedAccounts());
                postMessage("没有可用的已保存账号，请重新登录");
                screen.postValue(AppScreen.LOGIN);
                return;
            }
            loadRoles(restored, generation, false, true);
        });
    }

    public void submitWebCookies(List<String> cookieHeaders) {
        int generation = beginAuthAttempt();
        authTask = executor.submit(() -> {
            AuthSession result = authRepository.acceptWebCookies(cookieHeaders);
            if (!isAuthCurrent(generation)) return;
            if (result == null) {
                postMessage("官方页面尚未返回登录凭证，请确认页面已经显示登录成功");
                return;
            }
            loadRoles(result, generation, true, false);
        });
    }

    public void startQrLogin() {
        int generation = beginAuthAttempt();
        screen.setValue(AppScreen.QR_LOGIN);
        qrUrl.setValue(null);
        qrStatus.setValue("正在生成登录二维码…");
        authTask = executor.submit(() -> {
            try {
                QrLogin login = authRepository.createQrLogin();
                if (!isAuthCurrent(generation)) return;
                qrUrl.postValue(login.getUrl());
                qrStatus.postValue("请使用米游社扫描并确认登录");
                synchronized (this) {
                    if (!isAuthCurrent(generation)) return;
                    qrPolling = executor.scheduleWithFixedDelay(
                            () -> pollQr(login, generation), 1, 2, TimeUnit.SECONDS);
                }
            } catch (Exception error) {
                failAuth(generation, "无法生成登录二维码", error);
            }
        });
    }

    public void selectRole(GameRole role) {
        cancelSync();
        cancelImport();
        selectedRole.setValue(role);
        AuthSession activeSession = session;
        if (activeSession != null) {
            try {
                authRepository.setSelectedUid(activeSession.getAccountId(), role.getUid());
            } catch (RuntimeException error) {
                fail("无法保存角色选择", error);
            }
        }
        observeRecords(role.getUid());
    }

    public synchronized void sync() {
        AuthSession activeSession = session;
        GameRole role = selectedRole.getValue();
        if (importGuard.get()) {
            postMessage("正在导入记录，请等待导入完成");
            return;
        }
        if (activeSession == null || role == null || !syncGuard.compareAndSet(false, true)) return;
        int generation = syncGeneration.incrementAndGet();
        syncing.setValue(true);
        syncStatus.setValue("正在准备同步…");
        syncTask = executor.submit(() -> {
            try {
                int count = gachaRepository.sync(activeSession, role,
                        (pool, total) -> {
                            if (isSyncCurrent(generation, role)) {
                                syncStatus.postValue("正在同步" + pool + "，已读取 " + total + " 条");
                            }
                        });
                if (isSyncCurrent(generation, role)) {
                    syncStatus.postValue("同步完成，本次新增 " + count + " 条");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (isSyncCurrent(generation, role)) syncStatus.postValue("同步已取消");
            } catch (Exception error) {
                if (isSyncCurrent(generation, role)) {
                    fail("同步失败", error);
                    syncStatus.postValue("同步失败，可点击重试");
                }
            } finally {
                if (syncGeneration.get() == generation) {
                    syncGuard.set(false);
                    syncing.postValue(false);
                }
            }
        });
    }

    public synchronized void importJson(InputStream input) {
        GameRole role = selectedRole.getValue();
        if (syncGuard.get()) {
            closeQuietly(input);
            postMessage("正在同步记录，请等待同步完成后再导入");
            return;
        }
        if (input == null || role == null || !importGuard.compareAndSet(false, true)) {
            closeQuietly(input);
            return;
        }
        importing.setValue(true);
        int generation = importGeneration.incrementAndGet();
        importTask = executor.submit(() -> {
            try (InputStream ownedInput = input) {
                com.YSNB.yuanshen.core.model.GachaImportResult result =
                        gachaRepository.importJson(ownedInput, role.getUid());
                GameRole current = selectedRole.getValue();
                if (importGeneration.get() == generation && current != null
                        && current.getUid().equals(role.getUid())) {
                    postMessage(result.toDisplayText());
                }
            } catch (Exception error) {
                if (importGeneration.get() == generation) fail("导入失败", error);
            } finally {
                if (importGeneration.get() == generation) {
                    importGuard.set(false);
                    importing.postValue(false);
                }
            }
        });
    }

    public void logout(boolean clearCredentials) {
        cancelAuthentication();
        cancelSync();
        cancelImport();
        String accountId = session == null
                ? authRepository.getActiveAccountId() : session.getAccountId();
        try {
            if (clearCredentials) {
                authRepository.removeAccount(accountId);
            }
            authRepository.setManualLoginRequired(true);
            savedAccounts.setValue(authRepository.getSavedAccounts());
        } catch (RuntimeException error) {
            fail("无法更新账号登录信息", error);
            return;
        }
        session = null;
        roles.setValue(Collections.emptyList());
        selectedRole.setValue(null);
        if (recordSource != null) records.removeSource(recordSource);
        records.setValue(Collections.emptyList());
        statistics.setValue(GachaStatisticsCalculator.calculate(Collections.emptyList()));
        screen.setValue(AppScreen.LOGIN);
    }

    public void removeSavedAccount(String accountId) {
        executor.execute(() -> {
            try {
                authRepository.removeAccount(accountId);
                savedAccounts.postValue(authRepository.getSavedAccounts());
                postMessage("已移除该账号的登录信息");
            } catch (RuntimeException error) {
                fail("无法移除该账号", error);
            }
        });
    }

    private void restore() {
        int generation = beginAuthAttempt();
        authTask = executor.submit(() -> {
            List<SavedAccount> accounts = authRepository.getSavedAccounts();
            savedAccounts.postValue(accounts);
            if (!isAuthCurrent(generation)) return;
            scheduleMissingCommunityNicknameRefresh(accounts);
            if (accounts.isEmpty()) {
                screen.postValue(AppScreen.LOGIN);
                return;
            }
            if (authRepository.isManualLoginRequired()) {
                screen.postValue(AppScreen.LOGIN);
                return;
            }
            String accountId = authRepository.getActiveAccountId();
            if (accountId == null) accountId = accounts.get(0).getAccountId();
            AuthSession restored = authRepository.restoreSession(accountId);
            if (restored == null) {
                screen.postValue(AppScreen.LOGIN);
                return;
            }
            screen.postValue(AppScreen.MAIN);
            loadRoles(restored, generation, false, false);
        });
    }

    private void pollQr(QrLogin login, int generation) {
        if (!isAuthCurrent(generation)) return;
        try {
            QrLoginStatus status = authRepository.queryQrLogin(login);
            if (!isAuthCurrent(generation)) return;
            if (status.getState() == QrLoginStatus.State.SCANNED) {
                qrStatus.postValue("已扫描，请在米游社中确认登录");
            } else if (status.getState() == QrLoginStatus.State.EXPIRED) {
                cancelQrPolling();
                qrStatus.postValue("二维码已过期，请刷新");
            } else if (status.getState() == QrLoginStatus.State.CONFIRMED) {
                cancelQrPolling();
                qrStatus.postValue("登录已确认，正在读取账号…");
                AuthSession result = authRepository.completeQrLogin(status.getRawAccount());
                if (isAuthCurrent(generation)) loadRoles(result, generation, true, false);
            }
        } catch (Exception error) {
            cancelQrPolling();
            failAuth(generation, "扫码登录失败", error);
        }
    }

    private void loadRoles(AuthSession candidate, int generation, boolean persist,
                           boolean activateAccount) {
        try {
            List<GameRole> result = authRepository.getRoles(candidate);
            if (!isAuthCurrent(generation)) return;
            if (result.isEmpty()) {
                throw new IllegalStateException("当前米游社账号没有绑定原神角色");
            }
            String savedUid = authRepository.getSelectedUid(candidate.getAccountId());
            GameRole role = result.stream()
                    .filter(item -> item.getUid().equals(savedUid))
                    .findFirst()
                    .orElse(result.get(0));
            mainHandler.post(() -> {
                if (!isAuthCurrent(generation)) return;
                try {
                    if (persist) {
                        authRepository.saveSession(candidate);
                    } else if (activateAccount) {
                        authRepository.setActiveAccountId(candidate.getAccountId());
                    }
                } catch (RuntimeException error) {
                    authRepository.setManualLoginRequired(true);
                    fail("无法保存账号登录信息", error);
                    screen.setValue(AppScreen.LOGIN);
                    return;
                }
                authRepository.setManualLoginRequired(false);
                session = candidate;
                roles.setValue(result);
                try {
                    authRepository.setSelectedUid(candidate.getAccountId(), role.getUid());
                } catch (RuntimeException error) {
                    fail("无法保存角色选择", error);
                }
                savedAccounts.setValue(authRepository.getSavedAccounts());
                selectedRole.setValue(role);
                observeRecords(role.getUid());
                screen.setValue(AppScreen.MAIN);
                scheduleCommunityNicknameRefresh(candidate.getAccountId());
            });
        } catch (Exception error) {
            if (isAuthCurrent(generation)) {
                fail("无法读取原神角色", error);
                authRepository.setManualLoginRequired(true);
                screen.postValue(AppScreen.LOGIN);
            }
        }
    }

    private void refreshCommunityNickname(String accountId) {
        try {
            String nickname = authRepository.getCommunityNickname(accountId);
            if (Thread.currentThread().isInterrupted()
                    || nickname == null || nickname.isBlank()) return;
            authRepository.setCommunityNickname(accountId, nickname);
            savedAccounts.postValue(authRepository.getSavedAccounts());
        } catch (Exception error) {
            Log.w("YuanshenFlow", "无法刷新米游社社区昵称："
                    + error.getClass().getSimpleName() + "：" + error.getMessage());
        }
    }

    private synchronized void scheduleCommunityNicknameRefresh(String accountId) {
        cancelNicknameRefresh();
        nicknameTask = nicknameExecutor.submit(() -> {
            List<SavedAccount> accounts = authRepository.getSavedAccounts();
            for (String pendingAccountId
                    : CommunityNicknameRefreshPlanner.build(accounts, accountId)) {
                if (Thread.currentThread().isInterrupted()) return;
                refreshCommunityNickname(pendingAccountId);
            }
        });
    }

    private synchronized void scheduleMissingCommunityNicknameRefresh(List<SavedAccount> accounts) {
        List<String> pendingAccountIds =
                CommunityNicknameRefreshPlanner.build(accounts, null);
        if (pendingAccountIds.isEmpty()) return;
        cancelNicknameRefresh();
        nicknameTask = nicknameExecutor.submit(() -> {
            for (String accountId : pendingAccountIds) {
                if (Thread.currentThread().isInterrupted()) return;
                refreshCommunityNickname(accountId);
            }
        });
    }

    private synchronized void cancelNicknameRefresh() {
        if (nicknameTask != null) {
            nicknameTask.cancel(true);
            nicknameTask = null;
        }
    }

    private void observeRecords(String uid) {
        if (recordSource != null) records.removeSource(recordSource);
        recordSource = gachaRepository.observeRecords(uid);
        records.addSource(recordSource, value -> {
            List<GachaRecord> safeValue = value == null ? Collections.emptyList() : value;
            records.setValue(safeValue);
            statistics.setValue(GachaStatisticsCalculator.calculate(safeValue));
        });
    }

    private void fail(String prefix, Exception error) {
        String detail = error.getMessage();
        Log.w("YuanshenFlow", prefix + (detail == null ? "" : "：" + detail));
        postMessage(hasChinese(detail) ? prefix + "：" + detail : prefix + "，请稍后重试");
    }

    private static boolean hasChinese(String text) {
        if (text == null || text.isBlank()) return false;
        for (int index = 0; index < text.length(); index++) {
            if (Character.UnicodeScript.of(text.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private void postMessage(String text) { message.postValue(new Event<>(text)); }

    private void failAuth(int generation, String prefix, Exception error) {
        if (isAuthCurrent(generation)) fail(prefix, error);
    }

    private boolean isAuthCurrent(int generation) {
        return authGeneration.get() == generation;
    }

    private boolean isSyncCurrent(int generation, GameRole role) {
        GameRole current = selectedRole.getValue();
        return syncGeneration.get() == generation && session != null && current != null
                && current.getUid().equals(role.getUid());
    }

    private synchronized int beginAuthAttempt() {
        int generation = authGeneration.incrementAndGet();
        if (authTask != null) authTask.cancel(true);
        cancelNicknameRefresh();
        cancelQrPolling();
        return generation;
    }

    private synchronized void cancelAuthentication() {
        authGeneration.incrementAndGet();
        if (authTask != null) {
            authTask.cancel(true);
            authTask = null;
        }
        cancelNicknameRefresh();
        cancelQrPolling();
    }

    private void cancelSync() {
        syncGeneration.incrementAndGet();
        Future<?> task = syncTask;
        if (task != null) task.cancel(true);
        syncTask = null;
        syncGuard.set(false);
        syncing.setValue(false);
    }

    private synchronized void cancelImport() {
        importGeneration.incrementAndGet();
        Future<?> task = importTask;
        if (task != null) task.cancel(true);
        importTask = null;
        importGuard.set(false);
        importing.setValue(false);
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try {
            input.close();
        } catch (Exception ignored) {
        }
    }

    private synchronized void cancelQrPolling() {
        if (qrPolling != null) {
            qrPolling.cancel(false);
            qrPolling = null;
        }
    }

    @Override
    protected void onCleared() {
        cancelAuthentication();
        cancelSync();
        cancelImport();
        executor.shutdownNow();
        nicknameExecutor.shutdownNow();
    }
}
