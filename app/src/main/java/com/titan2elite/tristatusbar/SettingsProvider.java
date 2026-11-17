package com.titan2elite.tristatusbar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only bridge used by SystemUI; it exposes only non-sensitive display booleans.
 *
 * The provider runs in the module's own process, but LSPosed still redirects
 * {@code getSharedPreferences} to an isolated apexdata directory, so it would read
 * a stale copy of the settings. Read the real preferences file that MainActivity
 * writes instead, by resolving the path from {@link Context#getDataDir()}.
 */
public final class SettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!SettingsContract.METHOD_READ.equals(method) || getContext() == null) {
            return null;
        }
        Map<String, Object> map = readPrefs(getContext());
        Bundle result = new Bundle();
        for (Scene scene : Scene.values()) {
            result.putBoolean(scene.key, bool(map, scene.key, scene.defaultEnabled));
        }
        result.putBoolean(SettingsContract.KEY_HIDE_BLUETOOTH,
                bool(map, SettingsContract.KEY_HIDE_BLUETOOTH, false));
        result.putBoolean(SettingsContract.KEY_HIDE_RINGER,
                bool(map, SettingsContract.KEY_HIDE_RINGER, false));
        result.putBoolean(SettingsContract.KEY_HIDE_OTHER,
                bool(map, SettingsContract.KEY_HIDE_OTHER, false));
        result.putBoolean(SettingsContract.KEY_SHOW_BATTERY_PERCENT,
                bool(map, SettingsContract.KEY_SHOW_BATTERY_PERCENT, false));
        result.putBoolean(SettingsContract.KEY_LARGE_CLOCK,
                bool(map, SettingsContract.KEY_LARGE_CLOCK, false));
        return result;
    }

    /** Read the boolean entries out of the module's real settings.xml. */
    private static Map<String, Object> readPrefs(Context context) {
        Map<String, Object> map = new HashMap<>();
        File file = new File(context.getDataDir(),
                "shared_prefs/" + SettingsContract.PREFS_NAME + ".xml");
        if (!file.exists()) {
            return map;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG
                        && "boolean".equals(parser.getName())) {
                    String name = parser.getAttributeValue(null, "name");
                    String value = parser.getAttributeValue(null, "value");
                    if (name != null && value != null) {
                        map.put(name, Boolean.parseBoolean(value));
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to defaults if the file cannot be parsed.
        }
        return map;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : def;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }
}
