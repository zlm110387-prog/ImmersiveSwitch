package io.github.zlm110387.immersiveswitch;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

final class ShizukuCommandCompat {
    private ShizukuCommandCompat() {}

    static String setHidden(boolean hidden) throws Exception {
        return run(hidden
            ? "cmd statusbar send-disable-flag clock system-icons notification-icons"
            : "cmd statusbar send-disable-flag none");
    }

    static Boolean isHidden() throws Exception {
        String output = run("dumpsys statusbar | grep -E 'disable1=|mDisabled1=' | head -n 1");
        if (output.startsWith("ERROR:")) return null;
        if (output.isEmpty()) return null;
        String lower = output.toLowerCase();
        // DISABLE_CLOCK 0x00800000, DISABLE_SYSTEM_INFO 0x00100000,
        // DISABLE_NOTIFICATION_ICONS 0x00020000.
        int hexAt = lower.indexOf("0x");
        if (hexAt >= 0) {
            int end = hexAt + 2;
            while (end < lower.length() && Character.digit(lower.charAt(end), 16) >= 0) end++;
            try {
                long flags = Long.parseLong(lower.substring(hexAt + 2, end), 16);
                long wanted = 0x00800000L | 0x00100000L | 0x00020000L;
                return (flags & wanted) == wanted;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static String run(String command) throws Exception {
        if (!Shizuku.pingBinder() || Shizuku.getVersion() != 13
            || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Commands require authorized Shizuku API 13");
        }
        IRemoteProcess process = null;
        ParcelFileDescriptor stdout = null, stdin = null, stderr = null;
        InputStream input = null;
        FutureTask<String> output = null;
        try {
            process = IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(
                new String[]{"/system/bin/sh", "-c", command + " 2>&1"}, null, null);
            if (process == null) throw new IllegalStateException("Shizuku returned no remote process");
            stdin = process.getOutputStream();
            stderr = process.getErrorStream();
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
            if (!process.waitForTimeout(10, TimeUnit.SECONDS.name())) return "ERROR:命令超时";
            int exitCode = process.exitValue();
            String message = output.get(2, TimeUnit.SECONDS);
            return exitCode == 0 ? message : "ERROR:退出码 " + exitCode + ": " + message;
        } finally {
            if (process != null) try { process.destroy(); } catch (Exception ignored) {}
            if (input != null) try { input.close(); } catch (Exception ignored) {}
            else if (stdout != null) try { stdout.close(); } catch (Exception ignored) {}
            if (stdin != null) try { stdin.close(); } catch (Exception ignored) {}
            if (stderr != null) try { stderr.close(); } catch (Exception ignored) {}
            if (output != null) output.cancel(true);
        }
    }
}
