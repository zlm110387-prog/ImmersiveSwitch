package io.github.zlm110387.immersiveswitch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String TAG = "ImmersiveSwitch";
    private static final long START_DELAY_MS = 2000;
    private static final long CONNECTION_TIMEOUT_MS = 10000;
    private static final int MAX_ATTEMPTS = 2;
    private TextView status, result;
    private Button hide, restore;
    private IStatusBarService service;
    private boolean binding, busy, requested, compatibilityMode;
    private int attempts, generation;
    private String connectionError;
    private ServiceConnection connection;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs serviceArgs = new Shizuku.UserServiceArgs(
        new ComponentName("io.github.zlm110387.immersiveswitch", StatusBarService.class.getName()))
        .tag("statusbar").daemon(false).processNameSuffix("statusbar").version(2);

    private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(() -> {
        if (isDestroyed()) return;
        releaseService();
        attempts = 0;
        compatibilityMode = false;
        connectionError = null;
        refresh(true);
    });
    private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(() -> {
        releaseService();
        attempts = 0;
        compatibilityMode = false;
        connectionError = null;
        requested = false;
        refresh(false);
    });
    private final Shizuku.OnRequestPermissionResultListener permission = (code, grant) -> {
        if (code == 1) runOnUiThread(() -> refresh(false));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
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
                releaseService();
                compatibilityMode = false;
                attempts = 0;
                connectionError = null;
                status.setText(R.string.denied);
                if (ask && !requested && !Shizuku.shouldShowRequestPermissionRationale()) {
                    requested = true;
                    Shizuku.requestPermission(1);
                }
            } else if (compatibilityMode) {
                // The legacy remote-process transaction exists in server API 13 only.
                ready = Shizuku.getVersion() == 13;
                status.setText(ready ? R.string.compatibility : R.string.connection_timeout);
            } else if (service != null && service.asBinder().pingBinder()) {
                ready = true;
                status.setText(R.string.ready);
            } else if (!binding && attempts >= MAX_ATTEMPTS) {
                status.setText(getString(R.string.connection_failed, connectionError));
            } else {
                status.setText(R.string.connecting);
                if (!binding) beginBinding();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Shizuku state check failed", error);
            status.setText(getString(R.string.failed, error.toString()));
        }
        hide.setEnabled(ready && !busy);
        restore.setEnabled(ready && !busy);
    }

    private void beginBinding() {
        binding = true;
        final int attemptGeneration = ++generation;
        handler.postDelayed(() -> {
            if (isDestroyed() || attemptGeneration != generation) return;
            attempts++;
            try {
                if (!Shizuku.pingBinder()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    releaseService();
                    refresh(false);
                    return;
                }
                Log.i(TAG, "Binding UserService, attempt " + attempts);
                connection = new ServiceConnection() {
                    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                        if (isDestroyed() || attemptGeneration != generation) return;
                        if (binder == null || !binder.pingBinder()) {
                            bindingFailed(attemptGeneration, "Invalid UserService Binder");
                            return;
                        }
                        service = IStatusBarService.Stub.asInterface(binder);
                        binding = false;
                        handler.removeCallbacksAndMessages(null);
                        Log.i(TAG, "UserService connected: " + name.flattenToShortString());
                        refresh(false);
                    }
                    @Override public void onServiceDisconnected(ComponentName name) {
                        if (isDestroyed() || attemptGeneration != generation) return;
                        bindingFailed(attemptGeneration, "UserService disconnected");
                    }
                };
                handler.postDelayed(() -> {
                    if (attemptGeneration == generation && binding) {
                        bindingFailed(attemptGeneration, "No UserService Binder after 10 seconds");
                    }
                }, CONNECTION_TIMEOUT_MS);
                Shizuku.bindUserService(serviceArgs, connection);
            } catch (RuntimeException error) {
                bindingFailed(attemptGeneration, error.toString());
            }
        }, START_DELAY_MS);
    }

    private void bindingFailed(int attemptGeneration, String diagnostic) {
        if (isDestroyed() || attemptGeneration != generation) return;
        Log.w(TAG, "UserService connection failed: " + diagnostic);
        connectionError = diagnostic;
        releaseService();
        if (attempts >= MAX_ATTEMPTS && Shizuku.pingBinder() && Shizuku.getVersion() == 13) {
            compatibilityMode = true;
            Log.w(TAG, "Using server API 13 remote-process compatibility path");
        }
        refresh(false);
    }

    private void releaseService() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        ServiceConnection oldConnection = connection;
        connection = null;
        service = null;
        binding = false;
        if (oldConnection != null) {
            // remove=false clears the API's cached callback; remove=true stops the
            // server's record too, including a process that never returned its Binder.
            try { Shizuku.unbindUserService(serviceArgs, oldConnection, false); }
            catch (RuntimeException error) { Log.w(TAG, "Detach callback failed", error); }
            if (Shizuku.pingBinder()) {
                try { Shizuku.unbindUserService(serviceArgs, oldConnection, true); }
                catch (RuntimeException error) { Log.w(TAG, "Remove UserService failed", error); }
            }
        }
    }

    private void execute(boolean hidden) {
        final IStatusBarService current = service;
        final boolean useCompatibility = compatibilityMode;
        if ((!useCompatibility && current == null) || busy) return;
        busy = true;
        result.setText(R.string.running);
        refresh(false);
        executor.execute(() -> {
            String error;
            try {
                if (!Shizuku.pingBinder()
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Shizuku is unavailable or permission was revoked");
                }
                error = useCompatibility
                    ? ShizukuCommandCompat.setHidden(hidden) : current.setHidden(hidden);
            } catch (Exception exception) {
                Log.e(TAG, "Status bar command failed", exception);
                error = exception.toString();
            }
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
        releaseService();
        executor.shutdown();
        super.onDestroy();
    }
}
