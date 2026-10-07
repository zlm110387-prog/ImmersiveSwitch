package io.github.zlm110387.immersiveswitch;

import android.os.ParcelFileDescriptor;
import android.content.pm.PackageManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * API 13 compatibility path for ROMs where UserService cannot return its Binder.
 * Calls the same permission-checked server transaction used by the former
 * Shizuku.newProcess API. No reflection, local shell, or root process is used.
 * This legacy transaction must not be used on API 14+.
 */
final class ShizukuCommandCompat {
    private ShizukuCommandCompat() {}

    static String setHidden(boolean hidden) throws Exception {
        if (!Shizuku.pingBinder() || Shizuku.getVersion() != 13
            || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Compatibility commands require authorized Shizuku API 13");
        }
        String command = hidden
            ? "cmd statusbar send-disable-flag clock system-icons notification-icons"
            : "cmd statusbar send-disable-flag none";
        IRemoteProcess process = null;
        ParcelFileDescriptor stdout = null;
        ParcelFileDescriptor stdin = null;
        ParcelFileDescriptor stderr = null;
        InputStream input = null;
        FutureTask<String> output = null;
        try {
            process = IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(
                new String[]{"/system/bin/sh", "-c", command + " 2>&1"}, null, null);
            if (process == null) throw new IllegalStateException("Shizuku returned no remote process");
            stdin = process.getOutputStream();
            stderr = process.getErrorStream();
            // Commands never read stdin. Merge stderr above and close its unused pipe.
            if (stdin != null) stdin.close();
            if (stderr != null) stderr.close();
            stdout = process.getInputStream();
            if (stdout == null) throw new IllegalStateException("Shizuku returned no command output pipe");
            input = new ParcelFileDescriptor.AutoCloseInputStream(stdout);
            final InputStream stream = input;
            output = new FutureTask<>(() -> {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() < 8192) bytes.write(buffer, 0, Math.min(count, 8192 - bytes.size()));
                }
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
            });
            Thread reader = new Thread(output, "shizuku-command-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitForTimeout(10, TimeUnit.SECONDS.name())) {
                return "命令超时";
            }
            int exitCode = process.exitValue();
            String message = output.get(2, TimeUnit.SECONDS);
            return exitCode == 0 ? "" : "退出码 " + exitCode + ": " + message;
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Exception ignored) { }
            }
            if (input != null) {
                try { input.close(); } catch (Exception ignored) { }
            } else if (stdout != null) {
                try { stdout.close(); } catch (Exception ignored) { }
            }
            if (stdin != null) {
                try { stdin.close(); } catch (Exception ignored) { }
            }
            if (stderr != null) {
                try { stderr.close(); } catch (Exception ignored) { }
            }
            if (output != null) output.cancel(true);
        }
    }
}
