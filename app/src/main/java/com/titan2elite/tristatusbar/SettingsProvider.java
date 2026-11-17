package com.titan2elite.tristatusbar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

/** Read-only bridge used by SystemUI; it exposes only non-sensitive display booleans. */
public final class SettingsProvider extends ContentProvider {
    private static final String TAG = "DuoStatusBarProvider";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate pid=" + Process.myPid()
                + " dataDir=" + (getContext() == null ? "null" : getContext().getDataDir()));
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!SettingsContract.METHOD_READ.equals(method) || getContext() == null) {
            return null;
        }
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(
                SettingsContract.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        Log.d(TAG, "call pid=" + Process.myPid()
                + " dataDir=" + getContext().getDataDir()
                + " pkg=" + getContext().getPackageName()
                + " prefsFile=" + prefsFile(prefs));
        Bundle result = new Bundle();
        for (Scene scene : Scene.values()) {
            result.putBoolean(scene.key, prefs.getBoolean(scene.key, scene.defaultEnabled));
        }
        result.putBoolean(SettingsContract.KEY_HIDE_BLUETOOTH,
                prefs.getBoolean(SettingsContract.KEY_HIDE_BLUETOOTH, false));
        result.putBoolean(SettingsContract.KEY_HIDE_RINGER,
                prefs.getBoolean(SettingsContract.KEY_HIDE_RINGER, false));
        result.putBoolean(SettingsContract.KEY_HIDE_OTHER,
                prefs.getBoolean(SettingsContract.KEY_HIDE_OTHER, false));
        result.putBoolean(SettingsContract.KEY_SHOW_BATTERY_PERCENT,
                prefs.getBoolean(SettingsContract.KEY_SHOW_BATTERY_PERCENT, false));
        result.putBoolean(SettingsContract.KEY_LARGE_CLOCK,
                prefs.getBoolean(SettingsContract.KEY_LARGE_CLOCK, false));
        return result;
    }

    /** Best-effort extraction of the on-disk file backing a SharedPreferences instance. */
    private static String prefsFile(Object prefs) {
        Class<?> clazz = prefs.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("mFile");
                field.setAccessible(true);
                Object value = field.get(prefs);
                return value == null ? "null" : value.toString();
            } catch (Throwable ignored) {
                // try superclass
            }
            clazz = clazz.getSuperclass();
        }
        return "?";
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }
}
