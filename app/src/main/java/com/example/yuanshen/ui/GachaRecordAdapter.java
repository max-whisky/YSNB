package com.YSNB.yuanshen.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.YSNB.yuanshen.R;
import com.YSNB.yuanshen.core.model.GachaPool;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.domain.GachaPityTimelineCalculator;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public final class GachaRecordAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_CARD = 1;
    private static final int VIEW_PITY = 2;
    private static final int VIEW_YEAR = 3;

    public enum DisplayMode {
        CARD,
        PITY
    }

    private final List<GachaRecord> records = new ArrayList<>();
    private final List<GachaPityTimelineCalculator.Entry> timeline = new ArrayList<>();
    private DisplayMode displayMode = DisplayMode.CARD;

    public void submitList(List<GachaRecord> newRecords) {
        submitCards(newRecords);
    }

    public void submitCards(List<GachaRecord> newRecords) {
        displayMode = DisplayMode.CARD;
        records.clear();
        records.addAll(newRecords);
        timeline.clear();
        notifyDataSetChanged();
    }

    public void submitPityTimeline(List<GachaPityTimelineCalculator.Entry> newTimeline) {
        displayMode = DisplayMode.PITY;
        records.clear();
        timeline.clear();
        timeline.addAll(newTimeline);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (displayMode == DisplayMode.CARD) return VIEW_CARD;
        return timeline.get(position).getType()
                == GachaPityTimelineCalculator.Entry.Type.YEAR_HEADER ? VIEW_YEAR : VIEW_PITY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_YEAR) {
            return new YearViewHolder(inflater.inflate(R.layout.item_gacha_year, parent, false));
        }
        if (viewType == VIEW_PITY) {
            return new PityViewHolder(inflater.inflate(R.layout.item_gacha_pity, parent, false));
        }
        return new RecordViewHolder(inflater.inflate(R.layout.item_gacha_record, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RecordViewHolder) {
            ((RecordViewHolder) holder).bind(records.get(position));
        } else if (holder instanceof YearViewHolder) {
            ((YearViewHolder) holder).bind(timeline.get(position));
        } else if (holder instanceof PityViewHolder) {
            ((PityViewHolder) holder).bind(timeline.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return displayMode == DisplayMode.CARD ? records.size() : timeline.size();
    }

    static final class RecordViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView card;
        private final View accent;
        private final TextView pool;
        private final TextView rank;
        private final TextView name;
        private final TextView meta;

        RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_record);
            accent = itemView.findViewById(R.id.view_pool_accent);
            pool = itemView.findViewById(R.id.text_pool);
            rank = itemView.findViewById(R.id.text_rank);
            name = itemView.findViewById(R.id.text_name);
            meta = itemView.findViewById(R.id.text_meta);
        }

        void bind(GachaRecord record) {
            GachaPool recordPool = GachaPool.fromRecordType(record.getGachaType());
            GachaPoolStyle style = GachaPoolStyle.forPool(recordPool);
            accent.setBackgroundColor(style.getAccentColor());
            pool.setText(recordPool == null ? "其他祈愿" : recordPool.getDisplayName());
            pool.setTextColor(style.getAccentColor());
            pool.setBackground(rounded(pool, style.getSurfaceColor(), 999));

            int rankColor = rankColor(record.getRankType());
            int rankSurface = rankSurface(record.getRankType());
            rank.setText(record.getRankType() + " 星");
            rank.setTextColor(rankColor);
            rank.setBackground(rounded(rank, rankSurface, 999));

            name.setText(record.getName());
            name.setTextColor("武器".equals(record.getItemType())
                    ? GachaPoolStyle.getWeaponAccentColor()
                    : rankColor);
            meta.setText(record.getTime() + "  ·  " + record.getItemType());

            card.setCardBackgroundColor(0xFFFFFFFF);
            card.setStrokeColor(style.getSurfaceColor());
            card.setStrokeWidth(dp(itemView, 1));
            card.setRadius(dp(itemView, 15));
            card.setCardElevation(dp(itemView, 1));
        }
    }

    static final class YearViewHolder extends RecyclerView.ViewHolder {
        private final TextView year;

        YearViewHolder(@NonNull View itemView) {
            super(itemView);
            year = itemView.findViewById(R.id.text_timeline_year);
        }

        void bind(GachaPityTimelineCalculator.Entry entry) {
            year.setText(itemView.getResources().getString(R.string.history_year, entry.getYear()));
        }
    }

    static final class PityViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final FrameLayout track;
        private final TextView fill;
        private final TextView lostBadge;
        private int pullCount;
        private int pityCap;
        private boolean offBanner;

        PityViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.text_pity_avatar);
            track = itemView.findViewById(R.id.pity_bar_track);
            fill = itemView.findViewById(R.id.text_pity_bar_fill);
            lostBadge = itemView.findViewById(R.id.text_pity_lost_badge);
            track.setBackground(rounded(track, 0xFFEDE8DE, 7));
            lostBadge.setBackground(rounded(lostBadge, 0xFFE5483F, 999));
            track.addOnLayoutChangeListener((view, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) ->
                    updateFillWidth());
        }

        void bind(GachaPityTimelineCalculator.Entry entry) {
            boolean current = entry.getType()
                    == GachaPityTimelineCalculator.Entry.Type.CURRENT_PITY;
            GachaRecord record = entry.getRecord();
            pullCount = entry.getPullCount();
            pityCap = GachaPityTimelineCalculator.pityCap();
            offBanner = entry.isOffBanner();

            avatar.setText(current ? "?" : firstCharacter(record.getName()));
            avatar.setBackground(rounded(avatar, current ? 0xFF8A745C : 0xFFB87920, 999));
            fill.setText(itemView.getResources().getString(R.string.history_pull_count, pullCount));
            fill.setBackground(rounded(fill, progressColor(pullCount), 7));
            lostBadge.setVisibility(offBanner ? View.VISIBLE : View.GONE);
            itemView.setContentDescription(current
                    ? itemView.getResources().getString(
                            R.string.history_current_pity_description, pullCount)
                    : offBanner
                    ? itemView.getResources().getString(
                            R.string.history_lost_record_pity_description,
                            record.getName(), pullCount)
                    : itemView.getResources().getString(
                            R.string.history_record_pity_description,
                            record.getName(), pullCount));
            track.post(this::updateFillWidth);
        }

        private void updateFillWidth() {
            int trackWidth = track.getWidth();
            if (trackWidth <= 0 || pityCap <= 0) return;
            float progress = Math.min(1f, pullCount / (float) pityCap);
            int width = Math.max(dp(itemView, 52), Math.round(trackWidth * progress));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fill.getLayoutParams();
            params.width = Math.min(trackWidth, width);
            fill.setLayoutParams(params);
            if (offBanner) {
                int badgeWidth = lostBadge.getWidth();
                if (badgeWidth <= 0) badgeWidth = dp(itemView, 24);
                int badgeX = Math.min(params.width + dp(itemView, 4), trackWidth - badgeWidth);
                lostBadge.setX(Math.max(0, badgeX));
            }
        }
    }

    private static int progressColor(int pullCount) {
        switch (GachaPityTimelineCalculator.colorTierFor(pullCount)) {
            case GREEN:
                return 0xFF2FA35B;
            case YELLOW:
                return 0xFFE7B53E;
            case RED:
            default:
                return 0xFFEF604C;
        }
    }

    private static String firstCharacter(String value) {
        if (value == null || value.isEmpty()) return "?";
        int end = value.offsetByCodePoints(0, 1);
        return value.substring(0, end);
    }

    private static int rankColor(int rankType) {
        if (rankType >= 5) return 0xFFB87920;
        if (rankType == 4) return 0xFF7650AA;
        return 0xFF506B84;
    }

    private static int rankSurface(int rankType) {
        if (rankType >= 5) return 0xFFFFF2DC;
        if (rankType == 4) return 0xFFF1EAFE;
        return 0xFFEAF0F5;
    }

    private static GradientDrawable rounded(View view, int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(view, radiusDp));
        return background;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
