package com.titan2elite.tristatusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** SystemUI-side settings reader. */
final class SettingsBridge {
    interface Listener {
        void onSettingsChanged();
    }

    /** How long to wait before retrying a failed provider read. */
    private static final int RETRY_INTERVAL_MS = 3000;

    private static SettingsBridge instance;

    static synchronized SettingsBridge get(Context context) {
        if (instance == null) {
            instance = new SettingsBridge(context.getApplicationContext());
        }
        return instance;
    }

    private final ContentResolver resolver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final EnumMap<Scene, Boolean> values = new EnumMap<>(Scene.class);
    private final CopyOnWriteArrayList<WeakReference<Listener>> listeners =
            new CopyOnWriteArrayList<>();
    private boolean hideBluetooth;
    private boolean hideRinger;
    private boolean hideOther;
    private boolean showBatteryPercent;
    private boolean largeClock;
    private boolean loaded;

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            reload();
            if (loaded) {
                dispatch();
            }
        }
    };

    private SettingsBridge(Context context) {
        resolver = context.getContentResolver();
        for (Scene scene : Scene.values()) {
            values.put(scene, scene.defaultEnabled);
        }
        resolver.registerContentObserver(SettingsContract.CONTENT_URI, false,
                new ContentObserver(handler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        reload();
                        dispatch();
                    }
                });
        reload();
    }

    boolean isEnabled(Scene scene) {
        Boolean value = values.get(scene);
        return value == null ? scene.defaultEnabled : value;
    }

    boolean shouldHideBluetooth() {
        return hideBluetooth;
    }

    boolean shouldHideRinger() {
        return hideRinger;
    }

    boolean shouldHideOther() {
        return hideOther;
    }

    boolean shouldShowBatteryPercent() {
        return showBatteryPercent;
    }

    boolean shouldUseLargeClock() {
        return largeClock;
    }

    void addListener(Listener listener) {
        listeners.add(new WeakReference<>(listener));
    }

    private void reload() {
        try {
            Bundle result = resolver.call(SettingsContract.CONTENT_URI,
                    SettingsContract.METHOD_READ, null, null);
            if (result != null) {
                for (Scene scene : Scene.values()) {
                    values.put(scene, result.getBoolean(scene.key, scene.defaultEnabled));
                }
                hideBluetooth = result.getBoolean(SettingsContract.KEY_HIDE_BLUETOOTH, false);
                hideRinger = result.getBoolean(SettingsContract.KEY_HIDE_RINGER, false);
                hideOther = result.getBoolean(SettingsContract.KEY_HIDE_OTHER, false);
                showBatteryPercent = result.getBoolean(
                        SettingsContract.KEY_SHOW_BATTERY_PERCENT, false);
                largeClock = result.getBoolean(
                        SettingsContract.KEY_LARGE_CLOCK, false);
                loaded = true;
                handler.removeCallbacks(retryRunnable);
            } else {
                scheduleRetry();
            }
        } catch (Throwable ignored) {
            // The provider is not available yet (e.g. direct-boot before the user unlocks);
            // retry a little later instead of silently keeping the defaults forever.
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (loaded) {
            return;
        }
        handler.removeCallbacks(retryRunnable);
        handler.postDelayed(retryRunnable, RETRY_INTERVAL_MS);
    }

    private void dispatch() {
        for (WeakReference<Listener> reference : listeners) {
            Listener listener = reference.get();
            if (listener == null) {
                listeners.remove(reference);
            } else {
                handler.post(listener::onSettingsChanged);
            }
        }
    }
}
