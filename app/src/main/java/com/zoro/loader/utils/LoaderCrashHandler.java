package com.zoro.loader.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Logs uncaught Java exceptions to loader_log.txt, then delegates to the
 * previously installed handler so normal crash behaviour is unchanged.
 */
public class LoaderCrashHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler previous;

    public LoaderCrashHandler() {
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            printWriter.println(LoaderLogger.timestamp() + " [CRASH] thread=" + thread.getName());
            throwable.printStackTrace(printWriter);
            printWriter.flush();
            LoaderLogger.append(stringWriter.toString());
        } catch (Throwable ignored) {
            // Never interfere with the crash path.
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable);
        }
    }
}
