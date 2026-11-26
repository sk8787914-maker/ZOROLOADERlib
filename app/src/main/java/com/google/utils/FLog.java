package com.google.utils;

import android.util.Log;

import com.google.BuildConfig;

public class FLog {
    public static final String TAG = FLog.class.getSimpleName();

    public static void debug(String msg) {
        write("D", msg);
    }

    public static void info(String msg) {
        write("I", msg);
    }

    public static void warning(String msg) {
        write("W", msg);
    }

    public static void error(String msg) {
        write("E", msg);
    }

    private static void write(String level, String msg) {
        // This class runs in virtual processes too. Never let diagnostics throw.
        try {
            String line = LoaderLogger.timestamp() + " [" + level + "/" + TAG + "] " + msg;
            LoaderLogger.append(line);
        } catch (Throwable ignored) {
            // Swallowed on purpose.
        }
        if (BuildConfig.DEBUG) {
            try {
                if ("E".equals(level)) {
                    Log.e(TAG, msg);
                } else if ("W".equals(level)) {
                    Log.w(TAG, msg);
                } else if ("I".equals(level)) {
                    Log.i(TAG, msg);
                } else {
                    Log.d(TAG, msg);
                }
            } catch (Throwable ignored) {
                // Swallowed on purpose.
            }
        }
    }
}
