package com.google.utils;

import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes loader-side diagnostics to Download/loader_log.txt.
 * Rotates to loader_log.1.txt after 5 MB.
 *
 * IMPORTANT: this class is also loaded inside BlackBox virtual processes (:p0).
 * There, Environment.getExternalStoragePublicDirectory() can throw
 * ArrayIndexOutOfBoundsException because no storage volumes are mapped.
 * Logging must NEVER throw, otherwise the virtual process dies at startup.
 */
public final class LoaderLogger {
    private static final String FILE_NAME = "loader_log.txt";
    private static final String BACKUP_NAME = "loader_log.1.txt";
    private static final long MAX_SIZE = 5L * 1024L * 1024L;
    private static final Object LOCK = new Object();

    private LoaderLogger() {
    }

    public static File getLogFile() {
        try {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME);
        } catch (Throwable ignored) {
            // Virtual process / no storage volumes: use the literal path and fail soft below.
            try {
                return new File("/sdcard/Download", FILE_NAME);
            } catch (Throwable ignoredToo) {
                return null;
            }
        }
    }

    public static void append(String line) {
        if (line == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                File file = getLogFile();
                if (file == null) {
                    return;
                }
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                if (file.exists() && file.length() + line.getBytes("UTF-8").length > MAX_SIZE) {
                    File backup = new File(file.getParentFile(), BACKUP_NAME);
                    if (backup.exists()) {
                        backup.delete();
                    }
                    file.renameTo(backup);
                }
                FileWriter writer = new FileWriter(file, true);
                try {
                    writer.write(line);
                    if (!line.endsWith("\n")) {
                        writer.write("\n");
                    }
                } finally {
                    writer.close();
                }
            } catch (Throwable ignored) {
                // Diagnostics must never affect the host or virtual process.
            }
        }
    }

    public static String timestamp() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (Throwable ignored) {
            return "0000-00-00 00:00:00.000";
        }
    }
}
