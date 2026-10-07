package io.github.zlm110387.immersiveswitch;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String TAG = "ImmersiveSwitch";
    private TextView shizukuStatus, stateText, detail;
    private Switch toggle;
    private boolean busy, requested, suppressToggle, hidden;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Shizuku.OnBinderReceivedListener received =
        () -> runOnUiThread(() -> refresh(true));
    private final Shizuku.OnBinderDeadListener dead =
        () -> runOnUiThread(() -> refresh(false));
    private final Shizuku.OnRequestPermissionResultListener permission = (code, grant) -> {
        if (code == 1) runOnUiThread(() -> refresh(false));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        buildUi();
        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addBinderReceivedListenerSticky(received);
        refresh(true);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void buildUi() {
        int bg = Color.rgb(247, 247, 249);
        int card = Color.WHITE;
        int primary = Color.rgb(32, 33, 36);
        int secondary = Color.rgb(105, 105, 112);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(24), dp(18), dp(24), dp(24));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(24) + insets.getSystemWindowInsetLeft(),
                dp(18) + insets.getSystemWindowInsetTop(),
                dp(24) + insets.getSystemWindowInsetRight(),
                dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(primary);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.subtitle);
        subtitle.setTextColor(secondary);
        subtitle.setTextSize(15);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        statusCard.setBackground(rounded(card, 22));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(26);
        root.addView(statusCard, statusParams);

        TextView statusLabel = new TextView(this);
        statusLabel.setText(R.string.shizuku_label);
        statusLabel.setTextColor(secondary);
        statusLabel.setTextSize(13);
        statusCard.addView(statusLabel);

        shizukuStatus = new TextView(this);
        shizukuStatus.setTextColor(primary);
        shizukuStatus.setTextSize(17);
        shizukuStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams ss = new LinearLayout.LayoutParams(-1, -2);
        ss.topMargin = dp(5);
        statusCard.addView(shizukuStatus, ss);

        LinearLayout switchCard = new LinearLayout(this);
        switchCard.setOrientation(LinearLayout.HORIZONTAL);
        switchCard.setGravity(Gravity.CENTER_VERTICAL);
        switchCard.setPadding(dp(20), dp(20), dp(16), dp(20));
        switchCard.setBackground(rounded(card, 22));
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-1, -2);
        switchParams.topMargin = dp(14);
        root.addView(switchCard, switchParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        switchCard.addView(labels, labelsParams);

        TextView switchTitle = new TextView(this);
        switchTitle.setText(R.string.switch_title);
        switchTitle.setTextColor(primary);
        switchTitle.setTextSize(18);
        switchTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(switchTitle);

        stateText = new TextView(this);
        stateText.setTextColor(secondary);
        stateText.setTextSize(14);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.topMargin = dp(5);
        labels.addView(stateText, stateParams);

        toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setMinWidth(dp(58));
        switchCard.addView(toggle, new LinearLayout.LayoutParams(dp(64), dp(48)));

        detail = new TextView(this);
        detail.setTextColor(secondary);
        detail.setTextSize(13);
        detail.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(20);
        root.addView(detail, detailParams);

        setContentView(root);
        toggle.setOnCheckedChangeListener(this::onToggleChanged);
    }

    private void onToggleChanged(CompoundButton button, boolean checked) {
        if (suppressToggle || busy) return;
        execute(checked);
    }

    @Override protected void onResume() {
        super.onResume();
        if (shizukuStatus != null) refresh(true);
    }

    private void refresh(boolean ask) {
        if (isDestroyed()) return;
        boolean ready = false;
        try {
            if (!Shizuku.pingBinder()) {
                shizukuStatus.setText(R.string.waiting_short);
            } else if (Shizuku.getVersion() < 13) {
                shizukuStatus.setText(R.string.old_version);
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                shizukuStatus.setText(R.string.denied_short);
                if (ask && !requested && !Shizuku.shouldShowRequestPermissionRationale()) {
                    requested = true;
                    Shizuku.requestPermission(1);
                }
            } else if (Shizuku.getVersion() == 13) {
                ready = true;
                shizukuStatus.setText(R.string.ready_short);
                detectState();
            } else {
                shizukuStatus.setText(R.string.unsupported_version);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Shizuku state check failed", error);
            shizukuStatus.setText(R.string.error_short);
        }
        toggle.setEnabled(ready && !busy);
        if (!ready) {
            stateText.setText(R.string.unavailable);
            detail.setText(R.string.permission_hint);
        }
    }

    private void detectState() {
        if (busy) return;
        executor.execute(() -> {
            try {
                Boolean detected = ShizukuCommandCompat.isHidden();
                if (detected != null) handler.post(() -> applyState(detected, false));
            } catch (Exception error) {
                Log.d(TAG, "Could not detect status-bar flags", error);
            }
        });
    }

    private void applyState(boolean newHidden, boolean announce) {
        if (isDestroyed()) return;
        hidden = newHidden;
        suppressToggle = true;
        toggle.setChecked(hidden);
        suppressToggle = false;
        stateText.setText(hidden ? R.string.state_hidden : R.string.state_visible);
        if (announce) detail.setText(hidden ? R.string.hidden : R.string.restored);
        else if (!busy) detail.setText(R.string.tap_hint);
    }

    private void execute(boolean newHidden) {
        busy = true;
        toggle.setEnabled(false);
        stateText.setText(R.string.running);
        detail.setText("");
        executor.execute(() -> {
            String error;
            try {
                String output = ShizukuCommandCompat.setHidden(newHidden);
                error = output.startsWith("ERROR:") ? output.substring(6) : "";
            } catch (Exception exception) {
                Log.e(TAG, "Status bar command failed", exception);
                error = exception.toString();
            }
            final String diagnostic = error;
            handler.post(() -> {
                if (isDestroyed()) return;
                busy = false;
                if (diagnostic.isEmpty()) {
                    applyState(newHidden, true);
                } else {
                    suppressToggle = true;
                    toggle.setChecked(hidden);
                    suppressToggle = false;
                    detail.setText(getString(R.string.failed, diagnostic));
                }
                refresh(false);
            });
        });
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        executor.shutdown();
        super.onDestroy();
    }
}
