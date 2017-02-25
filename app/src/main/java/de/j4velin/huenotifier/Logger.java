package de.j4velin.huenotifier;

import android.database.Cursor;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

/**
 * Simple logger to log to logcat and logfile
 */
public abstract class Logger {

    private static FileWriter fw;
    private static final Date date = new Date();
    private final static String APP = "HueNotifier";

    public static void log(final Throwable ex) {
        if (!BuildConfig.DEBUG) return;
        log(ex.getClass().getName());
        log(ex.getMessage());
        for (StackTraceElement ste : ex.getStackTrace()) {
            log(ste.toString());
        }
        ex.printStackTrace();
    }

    public static void log(final Cursor c) {
        if (!BuildConfig.DEBUG) return;
        c.moveToFirst();
        String title = "";
        for (int i = 0; i < c.getColumnCount(); i++)
            title += c.getColumnName(i) + " | ";
        log(title);
        while (!c.isAfterLast()) {
            title = "";
            for (int i = 0; i < c.getColumnCount(); i++)
                title += c.getString(i) + " | ";
            log(title);
            c.moveToNext();
        }
    }

    @SuppressWarnings("deprecation")
    public static void log(final String msg) {
        if (!BuildConfig.DEBUG || msg == null) return;
        android.util.Log.d(APP, msg);
        try {
            if (fw == null) {
                fw = new FileWriter(new File(
                        Environment.getExternalStorageDirectory().toString() + "/" + APP + ".log"),
                        true);
            }
            date.setTime(System.currentTimeMillis());
            fw.write(date.toLocaleString() + " - " + msg + "\n");
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void finalize() throws Throwable {
        try {
            if (fw != null) fw.close();
        } finally {
            super.finalize();
        }
    }

}