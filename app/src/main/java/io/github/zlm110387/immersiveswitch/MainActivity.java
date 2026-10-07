package io.github.zlm110387.immersiveswitch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView status, result;
    private Button hide, restore;
    private IStatusBarService service;
    private boolean binding, busy, requested;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs serviceArgs = new Shizuku.UserServiceArgs(
        new ComponentName("io.github.zlm110387.immersiveswitch", StatusBarService.class.getName()))
        .daemon(false).processNameSuffix("statusbar").version(1);

    private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(() -> refresh(true));
    private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(() -> {
        service = null;
        binding = false;
        requested = false;
        refresh(false);
    });
    private final Shizuku.OnRequestPermissionResultListener permission = (code, grant) -> {
        if (code == 1) runOnUiThread(() -> refresh(false));
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                service = IStatusBarService.Stub.asInterface(binder);
                refresh(false);
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            runOnUiThread(() -> {
                service = null;
                binding = false;
                if (!isDestroyed()) refresh(false);
            });
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        // Keep the simple layout clear of system bars when targeting Android 15.
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(),
                padding + insets.getSystemWindowInsetTop(),
                padding + insets.getSystemWindowInsetRight(),
                padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(26);
        layout.addView(title);
        status = new TextView(this);
        status.setTextSize(16);
        layout.addView(status);
        hide = new Button(this);
        hide.setText(R.string.hide);
        layout.addView(hide);
        restore = new Button(this);
        restore.setText(R.string.restore);
        layout.addView(restore);
        result = new TextView(this);
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        layout.addView(result);
        setContentView(layout);
        hide.setOnClickListener(view -> execute(true));
        restore.setOnClickListener(view -> execute(false));
        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addBinderReceivedListenerSticky(received);
        refresh(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) refresh(true);
    }

    private void refresh(boolean ask) {
        if (isDestroyed()) return;
        boolean ready = false;
        try {
            if (!Shizuku.pingBinder()) {
                status.setText(R.string.waiting);
            } else if (Shizuku.getVersion() < 13) {
                status.setText(R.string.old_version);
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status.setText(R.string.denied);
                if (ask && !requested && !Shizuku.shouldShowRequestPermissionRationale()) {
                    requested = true;
                    Shizuku.requestPermission(1);
                }
            } else {
                ready = service != null && service.asBinder().isBinderAlive();
                status.setText(ready ? R.string.ready : R.string.connecting);
                if (!ready && !binding) {
                    binding = true;
                    Shizuku.bindUserService(serviceArgs, connection);
                }
            }
        } catch (RuntimeException error) {
            binding = false;
            status.setText(getString(R.string.failed, error.toString()));
        }
        hide.setEnabled(ready && !busy);
        restore.setEnabled(ready && !busy);
    }

    private void execute(boolean hidden) {
        final IStatusBarService current = service;
        if (current == null || busy) return;
        busy = true;
        result.setText(R.string.running);
        refresh(false);
        executor.execute(() -> {
            String error;
            try { error = current.setHidden(hidden); }
            catch (Exception exception) { error = exception.toString(); }
            final String diagnostic = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false;
                result.setText(diagnostic.isEmpty()
                    ? getString(hidden ? R.string.hidden : R.string.restored)
                    : getString(R.string.failed, diagnostic));
                refresh(false);
            });
        });
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        if (binding && Shizuku.pingBinder()) {
            try { Shizuku.unbindUserService(serviceArgs, connection, true); }
            catch (RuntimeException ignored) { }
        }
        executor.shutdown();
        super.onDestroy();
    }
}
