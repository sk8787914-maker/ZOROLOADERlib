package com.zoro.loader;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.zoro.loader.utils.FLog;
import com.zoro.loader.utils.FPrefs;
import com.zoro.loader.utils.LoaderCrashHandler;

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
        FLog.info("STEP attachBaseContext start");
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
        new LoaderCrashHandler();
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