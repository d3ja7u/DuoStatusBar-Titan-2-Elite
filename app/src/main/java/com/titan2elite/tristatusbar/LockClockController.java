package com.titan2elite.tristatusbar;

import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Hides the large left-side lock clock while the lock-scene switch is enabled. */
final class LockClockController implements SettingsBridge.Listener {
    private final View root;
    private final SettingsBridge settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<View, Integer> originalVisibility = new WeakHashMap<>();
    private final Map<View, Float> originalAlpha = new WeakHashMap<>();
    private boolean active;
    private boolean running;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            refresh();
            handler.postDelayed(this, 500);
        }
    };

    LockClockController(View root, SettingsBridge settings) {
        this.root = root;
        this.settings = settings;
        settings.addListener(this);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                apply();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                stop();
            }
        });
    }

    @Override
    public void onSettingsChanged() {
        apply();
    }

    void apply() {
        stop();
        active = settings.isEnabled(Scene.LOCK);
        if (!active) {
            restore();
            return;
        }
        refresh();
        start();
    }

    private void start() {
        if (running || !active || !root.isAttachedToWindow()) {
            return;
        }
        running = true;
        handler.postDelayed(ticker, 500);
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(ticker);
    }

    private void refresh() {
        if (!active) {
            return;
        }
        hideNamedClock("lockscreen_clock_view");
        hideNamedClock("lockscreen_clock_view_large");
    }

    private void hideNamedClock(String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
        if (id == 0) {
            return;
        }
        List<View> clocks = new ArrayList<>();
        collectById(root, id, clocks);
        for (View clock : clocks) {
            if (!originalVisibility.containsKey(clock)) {
                originalVisibility.put(clock, clock.getVisibility());
            }
            if (!originalAlpha.containsKey(clock)) {
                originalAlpha.put(clock, clock.getAlpha());
            }
            clock.setAlpha(0f);
            clock.setVisibility(View.GONE);
            if (clock instanceof TextView) {
                ((TextView) clock).setText("");
            }
        }
    }

    private void restore() {
        for (Map.Entry<View, Integer> entry : originalVisibility.entrySet()) {
            View clock = entry.getKey();
            clock.setVisibility(entry.getValue());
            Float alpha = originalAlpha.get(clock);
            if (alpha != null) {
                clock.setAlpha(alpha);
            }
            if (clock instanceof TextView) {
                ((TextView) clock).setText(currentTime());
            }
        }
        originalVisibility.clear();
        originalAlpha.clear();
    }

    private String currentTime() {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.is24HourFormat(root.getContext()) ? "Hm" : "hma";
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                .format(new Date());
    }

    private static void collectById(View view, int id, List<View> output) {
        if (view.getId() == id) {
            output.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectById(group.getChildAt(i), id, output);
            }
        }
    }
}
