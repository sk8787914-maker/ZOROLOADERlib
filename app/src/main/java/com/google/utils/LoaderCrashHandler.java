package com.google.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.os.Process;

import com.google.activity.LoginActivity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Last-line Java crash guard.
 *
 * - Logs every uncaught Java throwable to Download/loader_log.txt.
 * - Background-thread crashes are swallowed, so the process stays alive.
 * - Main-thread crashes are logged and the launcher is scheduled to come back
 *   automatically instead of showing the system "app has stopped" dialog.
 * - Native crashes (SIGABRT / invalid free) cannot be caught from Java; they are
 *   still recorded by the native/AAR diagnostics only.
 */
public final class LoaderCrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashGuard";
    private static final long RESTART_WINDOW_MS = 10_000L;

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler previous;
    private final AtomicInteger restarts = new AtomicInteger();
    private volatile long windowStart = System.currentTimeMillis();

    public LoaderCrashHandler(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.appContext = applicationContext != null ? applicationContext : context;
        this.previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        FLog.info(TAG + " installed (previous="
                + (previous == null ? "none" : previous.getClass().getSimpleName()) + ")");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        log(thread, throwable);

        if (thread == Looper.getMainLooper().getThread()) {
            scheduleRestart(nextRestartDelay());
            // Do not delegate to the previous handler: that shows the force-close dialog.
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Process.killProcess(Process.myPid());
            System.exit(0);
            return;
        }

        // Background thread died. Swallowing here keeps the app process alive.
    }

    /** Backs off if the app crashes repeatedly in a short window. */
    private long nextRestartDelay() {
        long now = System.currentTimeMillis();
        if (now - windowStart > RESTART_WINDOW_MS) {
            windowStart = now;
            restarts.set(0);
        }
        int count = restarts.incrementAndGet();
        return 600L + Math.min(count, 5) * 1000L;
    }

    private void scheduleRestart(long delayMillis) {
        try {
            Intent intent = appContext.getPackageManager()
                    .getLaunchIntentForPackage(appContext.getPackageName());
            if (intent == null) {
                intent = new Intent(appContext, LoginActivity.class);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(appContext, 77, intent, flags);

            AlarmManager alarmManager =
                    (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long trigger = System.currentTimeMillis() + delayMillis;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, trigger, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC, trigger, pendingIntent);
                }
            } else {
                pendingIntent.send();
            }
            FLog.info(TAG + " scheduled auto-restart in " + delayMillis + "ms");
        } catch (Throwable t) {
            FLog.error(TAG + " restart failed: " + t);
        }
    }

    private void log(Thread thread, Throwable throwable) {
        try {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            printWriter.println("[" + TAG + "] thread=" + thread.getName()
                    + " id=" + thread.getId() + " alive=" + thread.isAlive());
            throwable.printStackTrace(printWriter);
            printWriter.flush();
            FLog.error(stringWriter.toString());
        } catch (Throwable ignored) {
            // Diagnostics must never throw.
        }
    }
}
