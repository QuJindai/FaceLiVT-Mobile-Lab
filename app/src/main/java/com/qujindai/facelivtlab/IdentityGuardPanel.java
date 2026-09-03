package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Enrollment-page state/evidence/action surface for R5 duplicate prevention. */
public final class IdentityGuardPanel extends LinearLayout {
    public interface Listener {
        void onContinueConfirmation();
        void onSelectExistingCandidate(String identity);
        void onKeepExisting();
        void onAppendLearning();
        void onDeleteAndReenroll();
        void onHistoryVersionSelected(int version);
    }

    private final TextView title;
    private final TextView state;
    private final TextView evidence;
    private final LinearLayout candidates;
    private final Button continueButton;
    private final LinearLayout existingActions;
    private final Button keepButton;
    private final Button appendButton;
    private final Button deleteButton;
    private final TextView historyTitle;
    private final Spinner versions;
    private Listener listener;
    private boolean suppressVersionCallback;

    public IdentityGuardPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(10), dp(9), dp(10), dp(9));
        setBackgroundColor(Color.rgb(25, 39, 47));

        title = text("Identity Guard · 录入前身份防重", 15f, Color.WHITE, true);
        state = text("SUSPECTED · 等待有效 Probe", 13f, Color.rgb(255, 210, 100), true);
        evidence = text("新建身份保持锁定，直到 Guard 进入 CLEAR。", 10.5f, Color.rgb(195, 211, 215), false);
        addView(title);
        addView(state, lp(0, 4));
        addView(evidence, lp(0, 4));

        candidates = new LinearLayout(context);
        candidates.setOrientation(VERTICAL);
        addView(candidates, lp(0, 5));

        continueButton = button("继续确认");
        continueButton.setOnClickListener(v -> { if (listener != null) listener.onContinueConfirmation(); });
        addView(continueButton, lp(0, 5));

        existingActions = new LinearLayout(context);
        existingActions.setOrientation(HORIZONTAL);
        keepButton = button("保留现有");
        appendButton = button("追加学习 ×5");
        deleteButton = button("删除并重新录入");
        keepButton.setOnClickListener(v -> { if (listener != null) listener.onKeepExisting(); });
        appendButton.setOnClickListener(v -> { if (listener != null) listener.onAppendLearning(); });
        deleteButton.setOnClickListener(v -> { if (listener != null) listener.onDeleteAndReenroll(); });
        existingActions.addView(keepButton, weighted());
        existingActions.addView(appendButton, weighted());
        existingActions.addView(deleteButton, weighted());
        addView(existingActions, lp(0, 5));

        historyTitle = text("历史学习版本", 11f, Color.rgb(160, 220, 210), true);
        versions = new Spinner(context);
        versions.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (suppressVersionCallback || listener == null) return;
            Object item = versions.getItemAtPosition(position);
            if (item instanceof VersionItem) listener.onHistoryVersionSelected(((VersionItem) item).version);
        }));
        addView(historyTitle, lp(0, 5));
        addView(versions, lp(0, 2));

        render(null, new ArrayList<>(), new ArrayList<>(), -1, "");
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(IdentityGuardEngine.Snapshot snapshot, List<String> candidateNames,
                       List<Integer> historyVersions, int selectedVersion, String existingIdentity) {
        IdentityGuardEngine.State guardState = snapshot == null ? IdentityGuardEngine.State.SUSPECTED : snapshot.state;
        String candidate = snapshot == null ? "" : snapshot.candidateIdentity;
        String identity = existingIdentity == null || existingIdentity.trim().isEmpty() ? candidate : existingIdentity.trim();

        if (guardState == IdentityGuardEngine.State.CLEAR) {
            state.setText("CLEAR · 可新建身份");
            state.setTextColor(Color.rgb(120, 230, 150));
        } else if (guardState == IdentityGuardEngine.State.EXISTING || !identity.isEmpty() && existingIdentity != null && !existingIdentity.isEmpty()) {
            state.setText("EXISTING · 已有身份 " + identity + " · 禁止另存为新人");
            state.setTextColor(Color.rgb(255, 155, 120));
        } else {
            state.setText("SUSPECTED · 疑似重复 · 禁止新建");
            state.setTextColor(Color.rgb(255, 210, 100));
        }

        evidence.setText(buildEvidence(snapshot));
        renderCandidates(guardState, candidateNames);

        boolean existing = guardState == IdentityGuardEngine.State.EXISTING ||
                (existingIdentity != null && !existingIdentity.trim().isEmpty());
        continueButton.setVisibility(guardState == IdentityGuardEngine.State.SUSPECTED && !existing ? VISIBLE : GONE);
        existingActions.setVisibility(existing ? VISIBLE : GONE);
        historyTitle.setVisibility(existing ? VISIBLE : GONE);
        versions.setVisibility(existing && historyVersions != null && !historyVersions.isEmpty() ? VISIBLE : GONE);
        updateVersions(historyVersions, selectedVersion);
    }

    private void renderCandidates(IdentityGuardEngine.State guardState, List<String> candidateNames) {
        candidates.removeAllViews();
        if (guardState != IdentityGuardEngine.State.SUSPECTED || candidateNames == null) return;
        for (String name : candidateNames) {
            if (name == null || name.trim().isEmpty()) continue;
            String id = name.trim();
            Button button = button("这是已有身份 → " + id);
            button.setOnClickListener(v -> { if (listener != null) listener.onSelectExistingCandidate(id); });
            candidates.addView(button, lp(0, 3));
        }
    }

    private String buildEvidence(IdentityGuardEngine.Snapshot snapshot) {
        if (snapshot == null) return "等待有效 Probe · 新建身份暂时锁定";
        StringBuilder out = new StringBuilder();
        out.append(snapshot.reason);
        out.append(String.format(Locale.US, "\n时间证据：clear %d/5 · confirm %d/3", snapshot.cleanFrames, snapshot.confirmingFrames));
        for (ModelVariant variant : ModelVariant.values()) {
            IdentityGuardEngine.ModelEvidence e = snapshot.modelEvidence.get(variant);
            out.append("\n").append(variant.storageKey).append(" · ");
            if (e == null || e.top1Name.isEmpty() || !Float.isFinite(e.top1Score)) {
                out.append("无候选");
                continue;
            }
            out.append(e.top1Name).append("=").append(String.format(Locale.US, "%.3f", e.top1Score));
            if (e.marginAvailable && Float.isFinite(e.margin())) {
                out.append(" · margin ").append(String.format(Locale.US, "%.3f", e.margin()));
            } else {
                out.append(" · margin N/A");
            }
        }
        return out.toString();
    }

    private void updateVersions(List<Integer> historyVersions, int selectedVersion) {
        List<VersionItem> items = new ArrayList<>();
        if (historyVersions != null) for (Integer v : historyVersions) if (v != null && v > 0) items.add(new VersionItem(v));
        suppressVersionCallback = true;
        ArrayAdapter<VersionItem> adapter = new ArrayAdapter<>(getContext(), R.layout.spinner_item, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        versions.setAdapter(adapter);
        int selection = Math.max(0, items.size() - 1);
        for (int i = 0; i < items.size(); i++) if (items.get(i).version == selectedVersion) selection = i;
        if (!items.isEmpty()) versions.setSelection(selection, false);
        suppressVersionCallback = false;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(getContext());
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(10.5f);
        button.setMinHeight(dp(40));
        return button;
    }

    private LayoutParams lp(int left, int top) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(left);
        params.topMargin = dp(top);
        return params;
    }

    private LayoutParams weighted() {
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class VersionItem {
        final int version;
        VersionItem(int version) { this.version = version; }
        @Override public String toString() { return "V" + version; }
    }
}
