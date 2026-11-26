package com.google;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.google.utils.FLog;
import com.google.utils.FPrefs;
import com.google.utils.LoaderCrashHandler;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.core.system.api.MetaActivationManager;

public class BoxApplication extends Application {
    public static final String STATUS_BY = "online";
    public static BoxApplication gApp;

    // 🔐 Native method declaration
    private native String BoxApp();

    public static BoxApplication get() {
        return gApp;
    }

    private static boolean isLoginWebProcess(Context context) {
        try {
            int pid = android.os.Process.myPid();
            android.app.ActivityManager am = (android.app.ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> list =
                        am.getRunningAppProcesses();
                if (list != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo info : list) {
                        if (info != null && info.pid == pid && info.processName != null) {
                            return info.processName.endsWith(":login_web");
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // If the process name cannot be read, keep the normal loader path.
        }
        return false;
    }

    static {
        try {
            // 🔥 Library load ho rahi hai
            System.loadLibrary("MCoreEsp");
            FLog.info("STEP native lib MCoreEsp loaded");
        } catch (UnsatisfiedLinkError w) {
            FLog.error("STEP native lib load failed: " + (w.getMessage() != null ? w.getMessage() : "Load lib error"));
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // Install before any BlackBox/native work so early Java failures are caught too.
        new LoaderCrashHandler(base);
        FLog.info("STEP attachBaseContext start");
        // The isolated login WebView must be a plain Android process. BlackBox's
        // ActivityManager/PackageManager hooks break Chromium's renderer child
        // process ("ChildProcessConnection.start failed, trying again"), so the
        // login page never renders. Skip all virtualisation in :login_web.
        if (isLoginWebProcess(base)) {
            FLog.info("login_web process: BlackBox hooks skipped");
            return;
        }
        try {
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                // @Override HATAYA - ab compiler error nahi dega
                public String getHostPackageName() {
                    return base.getPackageName();
                }

                // @Override HATAYA
                public boolean isHideRoot() {
                    return true;
                }

                // @Override HATAYA
                public boolean isHideXposed() {
                    return true;
                }

                // @Override HATAYA
                public boolean isEnableDaemonService() {
                    return true;
                }
            });
            FLog.info("STEP attachBaseContext done");
        } catch (Exception e) {
            FLog.error("STEP attachBaseContext failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        if (isLoginWebProcess(this)) {
            FLog.info("login_web process ready without BlackBox hooks");
            return;
        }
        FLog.info("Loader started");
        BlackBoxCore.get().doCreate();

        try {
            FLog.info("STEP license native check start");
            String key = BoxApp();
            Log.d("LICENSE_DEBUG", "KEY: " + key);
            FLog.info("STEP license native check done");

            MetaActivationManager.activateSdk(BoxApp());
            FLog.info("STEP SDK activation done");
            //   MetaActivationManager.verifyLicense(Context, String)
            //   MetaActivationManager.verifyLicense(this, key);

        } catch (Exception exception) {
            exception.printStackTrace();
            FLog.error("License Verification Failed: " + exception.getMessage());
        }
    }
}