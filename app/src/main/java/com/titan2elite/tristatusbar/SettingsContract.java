package com.titan2elite.tristatusbar;

import android.net.Uri;

final class SettingsContract {
    static final String PREFS_NAME = "settings";
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".settings";
    static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/settings");
    static final String METHOD_READ = "read";

    static final String KEY_HIDE_BLUETOOTH = "hide_bluetooth";
    static final String KEY_HIDE_RINGER = "hide_ringer";
    static final String KEY_HIDE_OTHER = "hide_other";
    static final String KEY_SHOW_BATTERY_PERCENT = "show_battery_percent";

    private SettingsContract() {
    }
}
