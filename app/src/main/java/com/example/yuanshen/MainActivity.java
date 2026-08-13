package com.YSNB.yuanshen;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.core.model.GachaStatistics;
import com.YSNB.yuanshen.core.model.GameRole;
import com.YSNB.yuanshen.core.model.PoolStatistics;
import com.YSNB.yuanshen.core.network.MihoyoApiConfig;
import com.YSNB.yuanshen.domain.GachaPityTimelineCalculator;
import com.YSNB.yuanshen.ui.AppScreen;
import com.YSNB.yuanshen.ui.Event;
import com.YSNB.yuanshen.ui.GachaPoolStyle;
import com.YSNB.yuanshen.ui.GachaRecordAdapter;
import com.YSNB.yuanshen.ui.MainViewModel;
import com.YSNB.yuanshen.ui.MainViewModelFactory;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;


import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private static final long WEB_LOGIN_COOKIE_CHECK_INTERVAL_MS = 750L;
    private static final String PASSPORT_DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/128.0.0.0 Safari/537.36";
    private static final String[] COOKIE_URLS = {
            "https://bbs.mihoyo.com/",
            "https://user.mihoyo.com/",
            "https://passport-api.mihoyo.com/",
            "https://api-takumi.mihoyo.com/",
            "https://api-takumi.miyoushe.com/",
            "https://www.miyoushe.com/",
            "https://m.miyoushe.com/",
            "https://user.miyoushe.com/",
            "https://passport-api.miyoushe.com/",
            "https://passport-api.mihoyogift.com/"
    };
    private static final Set<String> LOGIN_HOSTS = Set.of(
            "bbs.mihoyo.com",
            "user.mihoyo.com",
            "passport-api.mihoyo.com",
            "webstatic.mihoyo.com",
            "act.mihoyo.com",
            "www.miyoushe.com",
            "m.miyoushe.com",
            "user.miyoushe.com",
            "passport-api.miyoushe.com",
            "passport-api.mihoyogift.com",
            "h5.miyoushe.com"
    );

    private MainViewModel viewModel;
    private FrameLayout screenContainer;
    private FrameLayout pageContainer;
    private AppScreen currentScreen;
    private int currentPageId = R.id.nav_overview;
    private int historyFilterPosition;
    private boolean statisticsTimeDescending;
    private final Set<GachaPool> expandedFiveStarPools = EnumSet.noneOf(GachaPool.class);
    private GachaRecordAdapter.DisplayMode historyDisplayMode =
            GachaRecordAdapter.DisplayMode.CARD;
    private WebView loginWebView;
    private ImageView qrImage;
    private ProgressBar qrProgress;
    private TextView qrStatus;
    private Spinner roleSpinner;
    private Button syncButton;
    private ProgressBar syncProgress;
    private TextView syncStatus;
    private boolean updatingRoleSpinner;
    private boolean loginSubmissionInProgress;
    private boolean importingJson;
    private View importJsonButton;
    private ProgressBar importJsonProgress;
    private final ActivityResultLauncher<String[]> jsonFilePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                try {
                    viewModel.importJson(getContentResolver().openInputStream(uri));
                } catch (Exception error) {
                    Toast.makeText(this, "无法打开所选文件", Toast.LENGTH_LONG).show();
                }
            });
    private final Runnable webLoginCookieWatcher = new Runnable() {
        @Override
        public void run() {
            if (loginWebView == null || loginSubmissionInProgress) return;
            maybeSubmitWebLogin();
            if (loginWebView != null && !loginSubmissionInProgress) {
                loginWebView.postDelayed(this, WEB_LOGIN_COOKIE_CHECK_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        screenContainer = findViewById(R.id.screen_container);
        applyWindowInsets();
        AppContainer container = ((YuanshenApplication) getApplication()).getContainer();
        viewModel = new ViewModelProvider(this, new MainViewModelFactory(container))
                .get(MainViewModel.class);
        observeState();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(screenContainer, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(screenContainer);
    }

    private void observeState() {
        viewModel.getScreen().observe(this, this::renderScreen);
        viewModel.getQrUrl().observe(this, this::renderQrCode);
        viewModel.getQrStatus().observe(this, value -> {
            if (qrStatus != null) qrStatus.setText(value);
        });
        viewModel.getRoles().observe(this, ignored -> updateRoleSpinner());
        viewModel.getSelectedRole().observe(this, ignored -> {
            updateRoleSpinner();
            renderCurrentPage();
        });
        viewModel.getRecords().observe(this, ignored -> renderCurrentPage());
        viewModel.getStatistics().observe(this, ignored -> renderCurrentPage());
        viewModel.getSyncing().observe(this, syncing -> {
            if (syncProgress != null) syncProgress.setVisibility(Boolean.TRUE.equals(syncing) ? View.VISIBLE : View.GONE);
            if (syncButton != null) syncButton.setEnabled(!Boolean.TRUE.equals(syncing));
        });
        viewModel.getSyncStatus().observe(this, value -> {
            if (syncStatus != null) syncStatus.setText(value);
        });
        viewModel.getImporting().observe(this, importing -> {
            importingJson = Boolean.TRUE.equals(importing);
            if (importJsonButton != null) importJsonButton.setEnabled(!importingJson);
            if (importJsonProgress != null) {
                importJsonProgress.setVisibility(importingJson ? View.VISIBLE : View.GONE);
            }
        });
        viewModel.getMessage().observe(this, this::showMessage);
    }

    private void showMessage(Event<String> event) {
        if (event == null) return;
        String message = event.consume();
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void renderScreen(AppScreen screen) {
        if (screen == null || screen == currentScreen) return;
        destroyLoginWebView();
        clearScreenReferences();
        currentScreen = screen;
        screenContainer.removeAllViews();
        if (screen == AppScreen.LOGIN) {
            renderLoginScreen();
        } else if (screen == AppScreen.WEB_LOGIN) {
            renderWebLoginScreen();
        } else if (screen == AppScreen.QR_LOGIN) {
            renderQrLoginScreen();
        } else {
            clearWebCookies();
            renderMainScreen();
        }
    }

    private void renderLoginScreen() {
        View root = inflate(R.layout.screen_login, screenContainer);
        root.findViewById(R.id.button_web_login).setOnClickListener(v -> viewModel.openWebLogin());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void renderWebLoginScreen() {
        View root = inflate(R.layout.screen_web_login, screenContainer);
        loginSubmissionInProgress = false;
        loginWebView = root.findViewById(R.id.login_webview);
        WebSettings settings = loginWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(PASSPORT_DESKTOP_USER_AGENT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(loginWebView, true);
        loginWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldBlockLoginNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlockLoginNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                maybeSubmitWebLogin();
            }
        });
        root.findViewById(R.id.button_back).setOnClickListener(v -> viewModel.backToLogin());
        loginWebView.removeCallbacks(webLoginCookieWatcher);
        loginWebView.post(webLoginCookieWatcher);
        if (!hasReusableWebSession()) {
            loginWebView.loadUrl(MihoyoApiConfig.LOGIN_PAGE);
        }
    }

    private void maybeSubmitWebLogin() {
        if (loginSubmissionInProgress) return;
        List<String> headers = collectWebCookies();
        boolean hasStoken = containsCookie(headers, "stoken")
                || containsCookie(headers, "stoken_v2");
        boolean hasCookieToken = containsCookie(headers, "cookie_token_v2");
        boolean hasAccountId = containsCookie(headers, "stuid")
                || containsCookie(headers, "account_id")
                || containsCookie(headers, "account_id_v2")
                || containsCookie(headers, "ltuid_v2");
        if ((hasStoken || hasCookieToken) && hasAccountId) {
            loginSubmissionInProgress = true;
            Log.i("YuanshenWebLogin", "已检测到官方登录凭证与账号标识，开始读取角色");
            viewModel.submitWebCookies(headers);
        }
    }

    private static boolean hasReusableWebSession() {
        List<String> headers = collectWebCookies();
        boolean hasCredential = containsCookie(headers, "stoken")
                || containsCookie(headers, "stoken_v2")
                || containsCookie(headers, "cookie_token_v2");
        boolean hasAccountId = containsCookie(headers, "stuid")
                || containsCookie(headers, "account_id")
                || containsCookie(headers, "account_id_v2")
                || containsCookie(headers, "ltuid_v2");
        return hasCredential && hasAccountId;
    }

    private static List<String> collectWebCookies() {
        CookieManager manager = CookieManager.getInstance();
        manager.flush();
        List<String> headers = new ArrayList<>();
        for (String url : COOKIE_URLS) {
            String value = manager.getCookie(url);
            if (value != null && !value.isBlank()) {
                headers.add(value);
            }
        }
        return headers;
    }

    private static boolean containsCookie(List<String> headers, String expectedName) {
        for (String header : headers) {
            for (String segment : header.split(";")) {
                int equals = segment.indexOf('=');
                if (equals > 0 && expectedName.equals(segment.substring(0, equals).trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderQrLoginScreen() {
        View root = inflate(R.layout.screen_qr_login, screenContainer);
        qrImage = root.findViewById(R.id.image_qr);
        qrProgress = root.findViewById(R.id.progress_qr);
        qrStatus = root.findViewById(R.id.text_qr_status);
        root.findViewById(R.id.button_refresh_qr).setOnClickListener(v -> viewModel.startQrLogin());
        root.findViewById(R.id.button_back).setOnClickListener(v -> viewModel.backToLogin());
        renderQrCode(viewModel.getQrUrl().getValue());
        String status = viewModel.getQrStatus().getValue();
        if (status != null) qrStatus.setText(status);
    }

    private void renderQrCode(String content) {
        if (qrImage == null) return;
        if (content == null || content.isBlank()) {
            qrImage.setImageDrawable(null);
            if (qrProgress != null) qrProgress.setVisibility(View.VISIBLE);
            return;
        }
        try {
            qrImage.setImageBitmap(createQrBitmap(content, 700));
            if (qrProgress != null) qrProgress.setVisibility(View.GONE);
        } catch (WriterException error) {
            Toast.makeText(this, "二维码生成失败", Toast.LENGTH_LONG).show();
        }
    }

    private void renderMainScreen() {
        View root = inflate(R.layout.screen_main, screenContainer);
        roleSpinner = root.findViewById(R.id.spinner_roles);
        syncButton = root.findViewById(R.id.button_sync);
        syncProgress = root.findViewById(R.id.progress_sync);
        syncStatus = root.findViewById(R.id.text_sync_status);
        pageContainer = root.findViewById(R.id.page_container);
        syncButton.setOnClickListener(v -> viewModel.sync());
        roleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                List<GameRole> roles = safeRoles();
                if (updatingRoleSpinner || position < 0 || position >= roles.size()) return;
                GameRole chosen = roles.get(position);
                GameRole selected = viewModel.getSelectedRole().getValue();
                if (selected == null || !selected.getUid().equals(chosen.getUid())) {
                    viewModel.selectRole(chosen);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        BottomNavigationView navigation = root.findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(item -> {
            currentPageId = item.getItemId();
            renderCurrentPage();
            return true;
        });
        navigation.setSelectedItemId(currentPageId);
        updateRoleSpinner();
        updateSyncState();
        renderCurrentPage();
    }

    private void updateRoleSpinner() {
        if (roleSpinner == null) return;
        List<GameRole> roles = safeRoles();
        List<String> labels = new ArrayList<>();
        for (GameRole role : roles) {
            labels.add(role.getNickname() + " · " + role.getRegionName() + " · " + role.getUid());
        }
        updatingRoleSpinner = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(adapter);
        GameRole selected = viewModel.getSelectedRole().getValue();
        if (selected != null) {
            for (int i = 0; i < roles.size(); i++) {
                if (roles.get(i).getUid().equals(selected.getUid())) {
                    roleSpinner.setSelection(i, false);
                    break;
                }
            }
        }
        updatingRoleSpinner = false;
    }

    private void updateSyncState() {
        Boolean syncing = viewModel.getSyncing().getValue();
        if (syncProgress != null) syncProgress.setVisibility(Boolean.TRUE.equals(syncing) ? View.VISIBLE : View.GONE);
        if (syncButton != null) syncButton.setEnabled(!Boolean.TRUE.equals(syncing));
        String status = viewModel.getSyncStatus().getValue();
        if (syncStatus != null && status != null) syncStatus.setText(status);
    }

    private void renderCurrentPage() {
        if (currentScreen != AppScreen.MAIN || pageContainer == null) return;
        pageContainer.removeAllViews();
        if (currentPageId == R.id.nav_history) {
            renderHistoryPageV2();
        } else if (currentPageId == R.id.nav_statistics) {
            renderStatisticsPageV2();
        } else if (currentPageId == R.id.nav_settings) {
            renderSettingsPage();
        } else {
            renderOverviewPageV2();
        }
    }

    private void renderOverviewPageV2() {
        View root = inflate(R.layout.page_overview, pageContainer);
        GameRole role = viewModel.getSelectedRole().getValue();
        TextView title = root.findViewById(R.id.text_role_title);
        TextView subtitle = root.findViewById(R.id.text_role_subtitle);
        if (role == null) {
            title.setText("正在读取角色…");
            subtitle.setText("");
        } else {
            title.setText(role.getNickname());
            subtitle.setText(role.getRegionName() + "  ·  UID " + role.getUid()
                    + "  ·  冒险等阶 " + role.getLevel());
        }
        ((TextView) root.findViewById(R.id.text_total_records))
                .setText(String.valueOf(safeRecords().size()));

        GridLayout grid = root.findViewById(R.id.pool_overview_grid);
        int columns = getResources().getConfiguration().screenWidthDp < 360 ? 1 : 2;
        grid.setColumnCount(columns);
        for (GachaPool pool : GachaPool.values()) {
            grid.addView(createOverviewPoolCard(pool, currentStatistics().forPool(pool), columns));
        }
    }

    private View createOverviewPoolCard(GachaPool pool, PoolStatistics statistics, int columns) {
        GachaPoolStyle style = GachaPoolStyle.forPool(pool);
        MaterialCardView card = new MaterialCardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(5), dp(4), dp(5));
        card.setLayoutParams(params);
        card.setCardBackgroundColor(style.getSurfaceColor());
        card.setStrokeColor(style.getAccentColor());
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = columns == 1 ? 18 : 14;
        content.setPadding(dp(horizontalPadding), dp(14), dp(horizontalPadding), dp(14));

        TextView badge = text(style.getShortName(), 12, Color.WHITE);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setPadding(dp(9), dp(3), dp(9), dp(3));
        badge.setBackground(roundedBackground(style.getAccentColor(), dp(999), 0, 0));
        content.addView(badge, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView pityTitle = text("当前垫数", 15, 0xFF283548);
        pityTitle.setTypeface(pityTitle.getTypeface(), Typeface.BOLD);
        pityTitle.setPadding(0, dp(9), 0, 0);
        content.addView(pityTitle);

        int total = statistics == null ? 0 : statistics.getTotalPulls();
        int pity = statistics == null ? 0 : statistics.getCurrentPity();
        int five = statistics == null ? 0 : statistics.getFiveStarCount();
        int four = statistics == null ? 0 : statistics.getFourStarCount();
        TextView pityValue = text(String.valueOf(pity), 30, style.getAccentColor());
        pityValue.setTypeface(pityValue.getTypeface(), Typeface.BOLD);
        pityValue.setPadding(0, dp(7), 0, 0);
        content.addView(pityValue);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(12), 0, 0);
        metrics.addView(createMetric("五星", String.valueOf(five), style.getAccentColor()));
        metrics.addView(createMetric("四星", String.valueOf(four), style.getAccentColor()));
        metrics.addView(createMetric("累计抽数", String.valueOf(total), style.getAccentColor()));
        content.addView(metrics);
        card.addView(content);
        return card;
    }

    private void renderHistoryPageV2() {
        View root = inflate(R.layout.page_history, pageContainer);
        LinearLayout filterContainer = root.findViewById(R.id.pool_filter_container);
        LinearLayout displayModeContainer =
                root.findViewById(R.id.history_display_mode_container);
        TextView cardMode = root.findViewById(R.id.history_mode_card);
        TextView pityMode = root.findViewById(R.id.history_mode_pity);
        RecyclerView list = root.findViewById(R.id.list_records);
        TextView empty = root.findViewById(R.id.text_history_empty);
        GachaRecordAdapter adapter = new GachaRecordAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        if (historyFilterPosition < 0 || historyFilterPosition > GachaPool.values().length) {
            historyFilterPosition = 0;
        }
        List<TextView> chips = new ArrayList<>();
        chips.add(createPoolFilterChip("全部", null, historyFilterPosition == 0));
        GachaPool[] pools = GachaPool.values();
        for (int index = 0; index < pools.length; index++) {
            GachaPool pool = pools[index];
            chips.add(createPoolFilterChip(GachaPoolStyle.forPool(pool).getShortName(),
                    pool, historyFilterPosition == index + 1));
        }
        for (int i = 0; i < chips.size(); i++) {
            final int position = i;
            TextView chip = chips.get(i);
            filterContainer.addView(chip);
            chip.setOnClickListener(v -> {
                historyFilterPosition = position;
                refreshHistoryList(adapter, list, empty, chips, displayModeContainer,
                        cardMode, pityMode);
                list.scrollToPosition(0);
            });
        }
        cardMode.setOnClickListener(v -> {
            historyDisplayMode = GachaRecordAdapter.DisplayMode.CARD;
            refreshHistoryList(adapter, list, empty, chips, displayModeContainer,
                    cardMode, pityMode);
            list.scrollToPosition(0);
        });
        pityMode.setOnClickListener(v -> {
            historyDisplayMode = GachaRecordAdapter.DisplayMode.PITY;
            refreshHistoryList(adapter, list, empty, chips, displayModeContainer,
                    cardMode, pityMode);
            list.scrollToPosition(0);
        });
        refreshHistoryList(adapter, list, empty, chips, displayModeContainer,
                cardMode, pityMode);
    }

    private void refreshHistoryList(
            GachaRecordAdapter adapter,
            RecyclerView list,
            TextView empty,
            List<TextView> chips,
            LinearLayout displayModeContainer,
            TextView cardMode,
            TextView pityMode
    ) {
        GachaPool[] pools = GachaPool.values();
        for (int index = 0; index < chips.size(); index++) {
            stylePoolFilterChip(chips.get(index),
                    index == 0 ? null : pools[index - 1], index == historyFilterPosition);
        }

        boolean canSwitchDisplay = historyFilterPosition > 0;
        displayModeContainer.setVisibility(canSwitchDisplay ? View.VISIBLE : View.GONE);
        GachaPool selectedPool = canSwitchDisplay ? pools[historyFilterPosition - 1] : null;
        styleHistoryDisplayMode(cardMode, selectedPool,
                historyDisplayMode == GachaRecordAdapter.DisplayMode.CARD);
        styleHistoryDisplayMode(pityMode, selectedPool,
                historyDisplayMode == GachaRecordAdapter.DisplayMode.PITY);

        List<GachaRecord> filtered = filterRecords(historyFilterPosition);
        if (canSwitchDisplay && historyDisplayMode == GachaRecordAdapter.DisplayMode.PITY) {
            adapter.submitPityTimeline(
                    GachaPityTimelineCalculator.calculate(selectedPool, filtered));
        } else {
            adapter.submitCards(filtered);
        }
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private TextView createPoolFilterChip(String label, GachaPool pool, boolean selected) {
        TextView chip = text(label, 14, 0xFF283548);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setTypeface(chip.getTypeface(), Typeface.BOLD);
        chip.setMinHeight(dp(38));
        chip.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMargins(dp(4), 0, dp(4), 0);
        chip.setLayoutParams(params);
        stylePoolFilterChip(chip, pool, selected);
        return chip;
    }

    private void stylePoolFilterChip(TextView chip, GachaPool pool, boolean selected) {
        int accent = pool == null ? 0xFF283548 : GachaPoolStyle.forPool(pool).getAccentColor();
        int surface = pool == null ? 0xFFF0F2F5 : GachaPoolStyle.forPool(pool).getSurfaceColor();
        chip.setTextColor(selected ? Color.WHITE : accent);
        chip.setBackground(roundedBackground(selected ? accent : surface, dp(999), dp(1), accent));
    }

    private void styleHistoryDisplayMode(TextView mode, GachaPool pool, boolean selected) {
        int accent = pool == null ? 0xFF283548 : GachaPoolStyle.forPool(pool).getAccentColor();
        int surface = pool == null ? 0xFFF0F2F5 : GachaPoolStyle.forPool(pool).getSurfaceColor();
        mode.setTextColor(selected ? Color.WHITE : accent);
        mode.setBackground(roundedBackground(selected ? accent : surface,
                dp(999), dp(1), accent));
    }

    private void renderOverviewPage() {
        View root = inflate(R.layout.page_overview, pageContainer);
        GameRole role = viewModel.getSelectedRole().getValue();
        TextView title = root.findViewById(R.id.text_role_title);
        TextView subtitle = root.findViewById(R.id.text_role_subtitle);
        if (role == null) {
            title.setText("正在读取角色…");
            subtitle.setText("");
        } else {
            title.setText(role.getNickname());
            subtitle.setText(role.getRegionName() + " · UID " + role.getUid() + " · 冒险等阶 " + role.getLevel());
        }
        PoolStatistics character = currentStatistics().forPool(GachaPool.CHARACTER_EVENT);
        TextView summary = root.findViewById(R.id.text_total_records);
        summary.setText(character == null ? "暂无记录" :
                "当前已垫 " + character.getCurrentPity() + " 抽\n五星 " + character.getFiveStarCount()
                        + " 个 · 四星 " + character.getFourStarCount() + " 个");
        ((TextView) root.findViewById(R.id.text_total_records))
                .setText(String.valueOf(safeRecords().size()));
    }

    private void renderHistoryPage() {
        View root = inflate(R.layout.page_history, pageContainer);
        Spinner filter = root.findViewById(R.id.pool_filter_container);
        List<String> labels = new ArrayList<>();
        labels.add("全部祈愿");
        for (GachaPool pool : GachaPool.values()) labels.add(pool.getDisplayName());
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(filterAdapter);
        RecyclerView list = root.findViewById(R.id.list_records);
        TextView empty = root.findViewById(R.id.text_history_empty);
        GachaRecordAdapter adapter = new GachaRecordAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                List<GachaRecord> filtered = filterRecords(position);
                adapter.submitList(filtered);
                empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        List<GachaRecord> records = safeRecords();
        adapter.submitList(records);
        empty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<GachaRecord> filterRecords(int position) {
        if (position <= 0) return safeRecords();
        GachaPool selectedPool = GachaPool.values()[position - 1];
        List<GachaRecord> filtered = new ArrayList<>();
        for (GachaRecord record : safeRecords()) {
            if (GachaPool.fromRecordType(record.getGachaType()) == selectedPool) filtered.add(record);
        }
        return filtered;
    }

    private void renderStatisticsPage() {
        View root = inflate(R.layout.page_statistics, pageContainer);
        LinearLayout container = root.findViewById(R.id.statistics_container);
        for (GachaPool pool : GachaPool.values()) {
            PoolStatistics statistics = currentStatistics().forPool(pool);
            container.addView(createStatisticsCard(pool, statistics));
        }
    }

    private View createStatisticsCard(GachaPool pool, PoolStatistics statistics) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView title = text(pool.getDisplayName(), 19, Color.rgb(40, 53, 72));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);
        if (statistics == null || statistics.getTotalPulls() == 0) {
            TextView empty = text("暂无已同步记录", 14, Color.rgb(105, 115, 134));
            empty.setPadding(0, dp(10), 0, 0);
            content.addView(empty);
        } else {
            String average = statistics.getAverageFiveStarPity() == null
                    ? "暂无完整五星区间"
                    : String.format(Locale.CHINA, "五星平均 %.1f 抽", statistics.getAverageFiveStarPity());
            TextView metrics = text(
                    "共 " + statistics.getTotalPulls() + " 抽 · 当前已垫 " + statistics.getCurrentPity() + " 抽\n"
                            + "五星 " + statistics.getFiveStarCount() + " 个 · 四星 " + statistics.getFourStarCount()
                            + " 个 · " + average,
                    14, Color.rgb(85, 96, 112));
            metrics.setPadding(0, dp(10), 0, 0);
            content.addView(metrics);
            if (!statistics.getFiveStarRecords().isEmpty()) {
                TextView heading = text("五星记录", 14, Color.rgb(124, 100, 66));
                heading.setPadding(0, dp(14), 0, dp(4));
                content.addView(heading);
                for (GachaRecord record : statistics.getFiveStarRecords()) {
                    content.addView(text(record.getName() + " · " + record.getTime(),
                            14, Color.rgb(190, 126, 34)));
                }
            }
        }
        card.addView(content);
        return card;
    }

    private void renderStatisticsPageV2() {
        View root = inflate(R.layout.page_statistics, pageContainer);
        LinearLayout container = root.findViewById(R.id.statistics_container);
        for (GachaPool pool : GachaPool.values()) {
            container.addView(createStatisticsCardV2(pool, currentStatistics().forPool(pool)));
        }
    }

    private View createStatisticsCardV2(GachaPool pool, PoolStatistics statistics) {
        GachaPoolStyle style = GachaPoolStyle.forPool(pool);
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(style.getSurfaceColor());
        card.setStrokeColor(style.getAccentColor());
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView badge = text(style.getShortName(), 12, Color.WHITE);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setPadding(dp(9), dp(3), dp(9), dp(3));
        badge.setBackground(roundedBackground(style.getAccentColor(), dp(999), 0, 0));
        headingRow.addView(badge);
        TextView title = text(pool.getDisplayName(), 18, 0xFF283548);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(dp(10), 0, 0, 0);
        headingRow.addView(title);
        content.addView(headingRow);

        if (statistics == null || statistics.getTotalPulls() == 0) {
            TextView empty = text("暂无已同步记录", 14, 0xFF697386);
            empty.setPadding(0, dp(10), 0, 0);
            content.addView(empty);
        } else {
            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.setPadding(0, dp(14), 0, 0);
            metrics.addView(createMetric("累计", statistics.getTotalPulls() + " 抽", style.getAccentColor()));
            metrics.addView(createMetric("当前垫数", statistics.getCurrentPity() + " 抽", style.getAccentColor()));
            metrics.addView(createMetric("五星 / 四星",
                    statistics.getFiveStarCount() + " / " + statistics.getFourStarCount(),
                    style.getAccentColor()));
            content.addView(metrics);

            String average = statistics.getAverageFiveStarPity() == null
                    ? "暂无完整五星区间"
                    : String.format(Locale.CHINA, "五星平均 %.1f 抽", statistics.getAverageFiveStarPity());
            TextView averageText = text(average, 13, 0xFF5D6878);
            averageText.setPadding(0, dp(12), 0, 0);
            content.addView(averageText);
            if (!statistics.getFiveStarRecords().isEmpty()) {
                int fiveStarCount = statistics.getFiveStarRecords().size();
                boolean initiallyExpanded = expandedFiveStarPools.contains(pool);
                LinearLayout fiveStarHeading = new LinearLayout(this);
                fiveStarHeading.setOrientation(LinearLayout.HORIZONTAL);
                fiveStarHeading.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView heading = text("五星记录（" + fiveStarCount + "）  "
                                + (initiallyExpanded ? "▲" : "▼"), 13,
                        style.getAccentColor());
                heading.setTypeface(heading.getTypeface(), Typeface.BOLD);
                heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
                heading.setMinHeight(dp(48));
                heading.setClickable(true);
                heading.setFocusable(true);
                fiveStarHeading.addView(heading, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                ImageButton sortOrder = new ImageButton(this);
                sortOrder.setImageResource(R.drawable.ic_time_sort);
                sortOrder.setScaleY(statisticsTimeDescending ? -1f : 1f);
                sortOrder.setContentDescription(statisticsTimeDescending
                        ? "当前时间倒序，点击切换为正序"
                        : "当前时间正序，点击切换为倒序");
                sortOrder.setScaleType(ImageView.ScaleType.CENTER);
                sortOrder.setPadding(dp(9), dp(9), dp(9), dp(9));
                sortOrder.setBackgroundResource(R.drawable.bg_statistics_sort_order);
                sortOrder.setOnClickListener(view -> {
                    statisticsTimeDescending = !statisticsTimeDescending;
                    renderCurrentPage();
                });
                fiveStarHeading.addView(sortOrder, new LinearLayout.LayoutParams(dp(40), dp(40)));
                content.addView(fiveStarHeading);

                LinearLayout fiveStarRecords = new LinearLayout(this);
                fiveStarRecords.setOrientation(LinearLayout.VERTICAL);
                fiveStarRecords.setVisibility(initiallyExpanded ? View.VISIBLE : View.GONE);
                List<GachaRecord> orderedRecords = new ArrayList<>(statistics.getFiveStarRecords());
                if (statisticsTimeDescending) Collections.reverse(orderedRecords);
                for (GachaRecord record : orderedRecords) {
                    LinearLayout recordRow = new LinearLayout(this);
                    recordRow.setOrientation(LinearLayout.HORIZONTAL);
                    recordRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    recordRow.setPadding(0, dp(3), 0, dp(3));

                    TextView name = text("★  " + record.getName(), 14, 0xFF9D661A);
                    name.setSingleLine(true);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    recordRow.addView(name, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    TextView time = text(record.getTime(), 13, 0xFF5D6878);
                    time.setGravity(android.view.Gravity.END);
                    time.setPadding(dp(12), 0, 0, 0);
                    recordRow.addView(time, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    fiveStarRecords.addView(recordRow);
                }
                content.addView(fiveStarRecords);
                heading.setOnClickListener(view -> {
                    boolean expanding = fiveStarRecords.getVisibility() != View.VISIBLE;
                    if (expanding) {
                        expandedFiveStarPools.add(pool);
                    } else {
                        expandedFiveStarPools.remove(pool);
                    }
                    fiveStarRecords.setVisibility(expanding ? View.VISIBLE : View.GONE);
                    heading.setText("五星记录（" + fiveStarCount + "）  " + (expanding ? "▲" : "▼"));
                });
            }
        }
        card.addView(content);
        return card;
    }

    private View createMetric(String label, String value, int valueColor) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text(value, 17, valueColor);
        valueView.setTypeface(valueView.getTypeface(), Typeface.BOLD);
        column.addView(valueView);
        TextView labelView = text(label, 11, 0xFF697386);
        labelView.setPadding(0, dp(2), 0, 0);
        column.addView(labelView);
        return column;
    }

    private GradientDrawable roundedBackground(int color, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (strokeWidth > 0) background.setStroke(strokeWidth, strokeColor);
        return background;
    }

    private void renderSettingsPage() {
        View root = inflate(R.layout.page_settings, pageContainer);
        GameRole role = viewModel.getSelectedRole().getValue();
        TextView account = root.findViewById(R.id.text_account_info);
        if (role == null) {
            account.setText("尚未读取角色信息");
        } else {
            account.setText("当前角色：" + role.getNickname() + "\n服务器：" + role.getRegionName()
                    + "\nUID：" + role.getUid());
        }
        root.findViewById(R.id.button_sync_now).setOnClickListener(v -> viewModel.sync());
        importJsonButton = root.findViewById(R.id.button_import_json);
        importJsonProgress = root.findViewById(R.id.progress_import_json);
        importJsonButton.setEnabled(!importingJson);
        importJsonProgress.setVisibility(importingJson ? View.VISIBLE : View.GONE);
        importJsonButton.setOnClickListener(v -> jsonFilePicker.launch(new String[]{
                "application/json", "text/json", "text/plain", "application/octet-stream"
        }));
        root.findViewById(R.id.button_logout).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("退出登录")
                        .setMessage("将清除本机保存的登录凭证，已同步的抽卡记录会继续保留。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("退出并清除", (dialog, which) -> {
                            CookieManager manager = CookieManager.getInstance();
                            manager.removeAllCookies(ignored -> {
                                manager.flush();
                                runOnUiThread(viewModel::logout);
                            });
                        })
                        .show());
    }

    private View inflate(int layoutId, FrameLayout parent) {
        View root = LayoutInflater.from(this).inflate(layoutId, parent, false);
        parent.addView(root);
        return root;
    }

    private List<GameRole> safeRoles() {
        List<GameRole> value = viewModel.getRoles().getValue();
        return value == null ? Collections.emptyList() : value;
    }

    private List<GachaRecord> safeRecords() {
        List<GachaRecord> value = viewModel.getRecords().getValue();
        return value == null ? Collections.emptyList() : value;
    }

    private GachaStatistics currentStatistics() {
        GachaStatistics value = viewModel.getStatistics().getValue();
        return value == null ? new GachaStatistics(Collections.emptyMap()) : value;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Bitmap createQrBitmap(String content, int size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    private static boolean isAllowedLoginUri(Uri uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && LOGIN_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private boolean shouldBlockLoginNavigation(Uri uri) {
        if (isAllowedLoginUri(uri)) return false;
        if (isOfficialAppPromotion(uri)) {
            Log.i("YuanshenWebLogin", "忽略米游社 App 推广跳转");
            return true;
        }
        Log.w("YuanshenWebLogin", "阻止登录跳转：" + uri.getScheme() + "://" + uri.getHost());
        Toast.makeText(this, "已阻止打开非米哈游页面", Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean isOfficialAppPromotion(Uri uri) {
        if ("mihoyobbs".equalsIgnoreCase(uri.getScheme())) return true;
        return "https".equalsIgnoreCase(uri.getScheme())
                && "download-bbs.miyoushe.com".equalsIgnoreCase(uri.getHost());
    }

    private static void clearWebCookies() {
        CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(ignored -> manager.flush());
    }

    private void clearScreenReferences() {
        pageContainer = null;
        qrImage = null;
        qrProgress = null;
        qrStatus = null;
        roleSpinner = null;
        syncButton = null;
        syncProgress = null;
        syncStatus = null;
    }

    private void destroyLoginWebView() {
        if (loginWebView == null) return;
        loginWebView.removeCallbacks(webLoginCookieWatcher);
        loginWebView.stopLoading();
        loginWebView.setWebViewClient(null);
        loginWebView.destroy();
        loginWebView = null;
    }

    @Override
    protected void onDestroy() {
        destroyLoginWebView();
        super.onDestroy();
    }
}
