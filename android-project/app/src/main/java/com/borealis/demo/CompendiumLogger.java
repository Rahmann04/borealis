package com.borealis.demo;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class CompendiumLogger {
    private static final String TAG = "CompendiumLogger";
    private static File logFile;
    private static Thread.UncaughtExceptionHandler previous;

    public static synchronized void initialize(Context context) {
        File base = context.getApplicationContext().getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File directory = new File(base, "logs");
        if (!directory.exists()) directory.mkdirs();
        logFile = new File(directory, "ERROR_RUNTIME.txt");
        log("INFO", "LOGGER_READY", "Android=" + Build.VERSION.RELEASE +
            " SDK=" + Build.VERSION.SDK_INT + " Device=" + Build.MODEL);
    }

    public static synchronized void log(String level, String stage, String message) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = time + " [" + level + "] [JAVA] [" + stage + "] " + message + "\n";
        try {
            if (logFile != null) {
                FileOutputStream out = new FileOutputStream(logFile, true);
                out.write(line.getBytes("UTF-8"));
                out.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Log write failed", e);
        }
        Log.println("FATAL".equals(level) || "ERROR".equals(level) ? Log.ERROR :
            "WARN".equals(level) ? Log.WARN : Log.INFO, TAG, line);
    }

    public static synchronized void logNative(String level, String message) {
        log(level, "NATIVE", message);
    }

    public static void installCrashLogger() {
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            log("FATAL", "UNCAUGHT_JAVA_EXCEPTION", "Thread=" + thread.getName() + "\n" + sw);
            if (previous != null) previous.uncaughtException(thread, error);
        });
        log("INFO", "CRASH_HANDLER_READY", "Java exception handler installed");
    }
}
