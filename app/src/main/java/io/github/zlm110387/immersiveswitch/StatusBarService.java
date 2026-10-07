package io.github.zlm110387.immersiveswitch;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs in Shizuku's shell process, never in the unprivileged app process. */
public class StatusBarService extends IStatusBarService.Stub {
    public StatusBarService() {}

    @Override public void destroy() { System.exit(0); }

    // Empty result means success; otherwise return the command's diagnostic.
    @Override public synchronized String setHidden(boolean hidden) {
        Process process = null;
        try {
            String[] command = hidden
                ? new String[]{"cmd", "statusbar", "send-disable-flag", "clock", "system-icons", "notification-icons"}
                : new String[]{"cmd", "statusbar", "send-disable-flag", "none"};
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "命令超时";
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() < 8192) output.write(buffer, 0, Math.min(count, 8192 - output.size()));
                }
            }
            String message = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 ? "" : "退出码 " + process.exitValue() + ": " + message;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return error.toString();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
