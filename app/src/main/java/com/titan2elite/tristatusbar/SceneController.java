package com.titan2elite.tristatusbar;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;

/** Owns one indicator in one SystemUI layout and restores everything when disabled. */
final class SceneController implements SettingsBridge.Listener {
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3})");

    private final Scene scene;
    private final View root;
    private final SettingsBridge settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<View, Integer> originalVisibility = new WeakHashMap<>();
    private final Map<View, Float> originalAlpha = new WeakHashMap<>();
    private final Map<ViewGroup, LayoutSnapshot> originalLayouts = new WeakHashMap<>();
    private final Map<View, ViewTreeObserver.OnPreDrawListener> hideGuards = new WeakHashMap<>();

    private ViewGroup target;
    private TriStatusView indicator;
    private boolean active;
    private boolean running;
    private int lastBattery = -1;
    private boolean lastCharging;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    SceneController(Scene scene, View root, SettingsBridge settings) {
        this.scene = scene;
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
        // A display-icon switch may have been turned off; restore first, then
        // suppress exactly the set selected by the latest settings.
        restoreOriginals();
        if (!settings.isEnabled(scene)) {
            active = false;
            removeIndicator();
            restoreOriginals();
            return;
        }

        View section = findSection();
        ViewGroup newTarget = section == null ? null : findTarget(section);
        if (newTarget == null) {
            active = false;
            removeIndicator();
            restoreOriginals();
            return;
        }

        if (indicator == null) {
            indicator = new TriStatusView(root.getContext());
        }
        if (indicator.getParent() != newTarget) {
            removeIndicator();
            target = newTarget;
            addIndicator(target);
        }
        target = newTarget;
        active = true;
        refresh();
        start();
    }

    private void start() {
        if (running || !active || !root.isAttachedToWindow()) {
            return;
        }
        running = true;
        handler.postDelayed(ticker, 1000);
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(ticker);
    }

    private void refresh() {
        if (!active || indicator == null || target == null) {
            return;
        }
        ensureIndicatorSpace();
        View section = findSection();
        if (section == null) {
            return;
        }

        TextView clock = asTextView(findFirstByName(section, "clock"));
        List<View> signalViews = new ArrayList<>();
        List<View> wifiGroups = new ArrayList<>();
        List<View> mobileGroups = new ArrayList<>();
        collectByName(section, "wifi_combo", wifiGroups);
        collectByName(section, "mobile_combo", mobileGroups);
        if (wifiGroups.isEmpty()) {
            collectByName(section, "wifi_group", wifiGroups);
        }
        if (mobileGroups.isEmpty()) {
            collectByName(section, "mobile_group", mobileGroups);
        }
        signalViews.addAll(wifiGroups);
        signalViews.addAll(mobileGroups);

        suppressTopmost(wifiGroups);
        suppressTopmost(mobileGroups);
        suppressAll(section, "battery", "batteryRemainingIcon",
                "battery_meter_view", "battery_meter_composable_view");
        if (scene != Scene.LOCK) {
            suppressAll(section, "clock");
        }
        if (scene == Scene.SHADE) {
            suppressAll(section, "carrier_group");
        }
        applyOptionalIconVisibility(section);

        ImageView wifi = findImageByName(section, "wifi_signal");
        ImageView mobile = findImageByName(section, "mobile_signal");
        boolean wifiConnected = isWifiConnected(root.getContext());
        boolean useWifi = wifiConnected && wifi != null;
        if (!useWifi && mobile == null && wifi != null) {
            useWifi = true;
        }
        ImageView selected = useWifi ? wifi : mobile;

        String time = clock == null || TextUtils.isEmpty(clock.getText())
                ? currentTime(root.getContext()) : clock.getText().toString();
        int color = clock == null ? Color.WHITE : clock.getCurrentTextColor();
        if (clock == null && selected != null && selected.getImageTintList() != null) {
            color = selected.getImageTintList().getDefaultColor();
        }

        BatteryState battery = readBattery(section);
        indicator.setVerticalMode(isVertical(target));
        String netLabel = useWifi ? null : NetworkLabelResolver.label(root.getContext());
        indicator.render(time, color, selected == null ? null : selected.getDrawable(),
                useWifi, battery.level, battery.charging, settings.shouldShowBatteryPercent(),
                readMobileLevel(mobile), netLabel);
    }

    private int readMobileLevel(ImageView mobile) {
        if (mobile != null && mobile.getDrawable() != null) {
            int level = mobile.getDrawable().getLevel();
            if (level > 0) {
                return Math.min(4, level);
            }
        }
        try {
            TelephonyManager telephony = (TelephonyManager) root.getContext()
                    .getSystemService(Context.TELEPHONY_SERVICE);
            SignalStrength strength = telephony == null ? null : telephony.getSignalStrength();
            if (strength != null) {
                return Math.max(0, Math.min(4, strength.getLevel()));
            }
        } catch (Throwable ignored) {
            // SystemUI normally has phone-state access; keep an empty dot row if unavailable.
        }
        return 0;
    }

    private void applyOptionalIconVisibility(View section) {
        boolean hideBluetooth = settings.shouldHideBluetooth();
        boolean hideRinger = settings.shouldHideRinger();
        boolean hideOther = settings.shouldHideOther();
        if (!hideBluetooth && !hideRinger && !hideOther) {
            return;
        }

        List<View> containers = new ArrayList<>();
        collectByName(section, "statusIcons", containers);
        for (View container : containers) {
            if (!(container instanceof ViewGroup)) {
                continue;
            }
            ViewGroup group = (ViewGroup) container;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                boolean bluetooth = isBluetoothIcon(child);
                boolean ringer = isRingerIcon(child);
                boolean signal = isSignalIcon(child);
                if ((hideBluetooth && bluetooth) || (hideRinger && ringer)
                        || (hideOther && !signal && !bluetooth && !ringer)) {
                    suppress(child);
                }
            }
        }
    }

    private boolean isBluetoothIcon(View view) {
        String type = iconType(view);
        return "bluetooth".equals(type) || "bluetooth_combo".equals(type)
                || containsAny(view, "bluetooth", "bluetooth_combo");
    }

    private boolean isRingerIcon(View view) {
        String type = iconType(view).toLowerCase(java.util.Locale.ROOT);
        if (type.contains("ringer") || type.contains("vibrat") || type.contains("volume")
                || type.contains("mute") || type.contains("silent")
                || containsAny(view, "ringer", "ringer_combo", "volume")) {
            return true;
        }
        CharSequence description = view.getContentDescription();
        if (description != null) {
            String text = description.toString().toLowerCase(java.util.Locale.ROOT);
            if (text.contains("静音") || text.contains("振动") || text.contains("ringer")
                    || text.contains("vibrat") || text.contains("mute")
                    || text.contains("silent")) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (isRingerIcon(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSignalIcon(View view) {
        String type = iconType(view);
        return "wifi".equals(type) || "mobile".equals(type)
                || "wifi_combo".equals(type) || "mobile_combo".equals(type)
                || containsAny(view, "wifi_signal", "mobile_signal");
    }

    private String iconType(View view) {
        try {
            Object slot = XposedHelpers.callMethod(view, "getSlot");
            if (slot != null) {
                return slot.toString();
            }
        } catch (Throwable ignored) {
            // Static shade icons do not implement StatusIconDisplayable.
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void suppressNamedTopmost(View section, String... names) {
        List<View> views = new ArrayList<>();
        for (String name : names) {
            collectByName(section, name, views);
        }
        suppressTopmost(views);
    }

    private boolean containsAny(View view, String... names) {
        for (String name : names) {
            if (findFirstByName(view, name) != null) {
                return true;
            }
        }
        return false;
    }

    private View findSection() {
        if (scene == Scene.SHADE) {
            return findFirstByName(root, "split_shade_status_bar");
        }
        return root;
    }

    private ViewGroup findTarget(View section) {
        if (scene == Scene.SHADE) {
            ViewGroup header = asViewGroup(findFirstByName(section, "shade_header_system_icons"));
            if (header != null) {
                return header;
            }
            return asViewGroup(findFirstByName(section, "hover_system_icons_container"));
        }
        ViewGroup systemIcons = asViewGroup(findFirstByName(section, "system_icons"));
        if (systemIcons != null) {
            return systemIcons;
        }
        return asViewGroup(findFirstByName(section, "system_icons_container"));
    }

    private void addIndicator(ViewGroup parent) {
        ViewGroup.LayoutParams params;
        // The shade header row is only 42px tall; request the full 28dp height and let the
        // indicator overflow it (ancestors are unclipped) instead of shrinking the ring.
        int height = scene == Scene.SHADE ? dp(28) : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, height,
                    Gravity.END | Gravity.CENTER_VERTICAL);
            frameParams.leftMargin = dp(8);
            params = frameParams;
        } else {
            LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, height);
            linearParams.gravity = Gravity.CENTER_VERTICAL;
            params = linearParams;
        }
        parent.addView(indicator, params);
    }

    private void removeIndicator() {
        if (indicator != null && indicator.getParent() instanceof ViewGroup) {
            ((ViewGroup) indicator.getParent()).removeView(indicator);
        }
    }

    private void restoreOriginals() {
        for (Map.Entry<View, ViewTreeObserver.OnPreDrawListener> entry : hideGuards.entrySet()) {
            entry.getKey().getViewTreeObserver().removeOnPreDrawListener(entry.getValue());
        }
        hideGuards.clear();
        for (Map.Entry<View, Integer> entry : originalVisibility.entrySet()) {
            View view = entry.getKey();
            view.setVisibility(entry.getValue());
            Float alpha = originalAlpha.get(view);
            if (alpha != null) {
                view.setAlpha(alpha);
            }
            if (view instanceof TextView && view.getId() == id("clock")) {
                ((TextView) view).setText(currentTime(root.getContext()));
            }
        }
        for (Map.Entry<ViewGroup, LayoutSnapshot> entry : originalLayouts.entrySet()) {
            entry.getValue().restore(entry.getKey());
        }
        originalVisibility.clear();
        originalAlpha.clear();
        originalLayouts.clear();
    }

    /**
     * Expands the target's under-sized ancestor containers so the ring (28dp tall) is not
     * clipped. The main status bar gives 39px and the shade header 42px, but the ring needs
     * 58px on this density; the lock screen already provides 58px and is left untouched.
     */
    private void ensureIndicatorSpace() {
        if (scene == Scene.SHADE) {
            // The shade header is motion-layout driven; keep the full ring height but let it
            // overflow the 42px row by disabling clipping along the ancestor chain.
            ViewGroup current = target;
            while (current != null && current != root) {
                if (current.getClipChildren() || current.getClipToPadding()) {
                    if (!originalLayouts.containsKey(current)) {
                        originalLayouts.put(current, LayoutSnapshot.capture(current));
                    }
                    current.setClipChildren(false);
                    current.setClipToPadding(false);
                }
                ViewParent parent = current.getParent();
                current = parent instanceof ViewGroup ? (ViewGroup) parent : null;
            }
            return;
        }
        int required = dp(28);
        ViewGroup current = target;
        while (current != null && current != root) {
            int height = current.getHeight();
            if (height > 0 && height < required) {
                if (!originalLayouts.containsKey(current)) {
                    originalLayouts.put(current, LayoutSnapshot.capture(current));
                }
                current.setMinimumHeight(required);
                ViewGroup.LayoutParams params = current.getLayoutParams();
                if (params != null && params.height != required) {
                    params.height = required;
                    current.setLayoutParams(params);
                }
                current.setClipChildren(false);
                current.setClipToPadding(false);
            }
            ViewParent parent = current.getParent();
            current = parent instanceof ViewGroup ? (ViewGroup) parent : null;
        }
    }

    private void suppressAll(View section, String... names) {
        for (String name : names) {
            List<View> views = new ArrayList<>();
            collectByName(section, name, views);
            for (View view : views) {
                suppress(view);
                if ("clock".equals(name) && view instanceof TextView) {
                    ((TextView) view).setText("");
                }
            }
        }
    }

    private void suppressTopmost(List<View> views) {
        for (View view : views) {
            boolean nested = false;
            ViewParent parent = view.getParent();
            while (parent instanceof View) {
                if (views.contains(parent)) {
                    nested = true;
                    break;
                }
                parent = parent.getParent();
            }
            if (!nested) {
                suppress(view);
                guardHidden(view);
            }
        }
    }

    private void suppress(View view) {
        int currentVisibility = view.getVisibility();
        if (!originalVisibility.containsKey(view) || currentVisibility == View.VISIBLE) {
            originalVisibility.put(view, currentVisibility);
        }
        if (!originalAlpha.containsKey(view)) {
            originalAlpha.put(view, view.getAlpha());
        }
        view.setAlpha(0f);
        if (view.getVisibility() != View.GONE) {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * The shade expand animation re-shows the system wifi/mobile icons that we suppressed with
     * GONE. Keep forcing them hidden on every draw pass so they never briefly overlap our
     * combined indicator during the transition.
     */
    private void guardHidden(View view) {
        if (hideGuards.containsKey(view)) {
            return;
        }
        ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (view.getVisibility() != View.GONE) {
                    view.setVisibility(View.GONE);
                }
                return true;
            }
        };
        hideGuards.put(view, listener);
        view.getViewTreeObserver().addOnPreDrawListener(listener);
    }

    private BatteryState readBattery(View section) {
        int level = -1;
        boolean charging = lastCharging;
        try {
            BatteryManager manager = (BatteryManager) root.getContext().getSystemService(
                    Context.BATTERY_SERVICE);
            if (manager != null) {
                level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                charging = manager.isCharging();
            }
        } catch (Throwable ignored) {
        }

        View batteryView = findFirstByName(section, "batteryRemainingIcon");
        if (batteryView == null) {
            batteryView = findFirstByName(section, "battery");
        }
        CharSequence description = batteryView == null ? null : batteryView.getContentDescription();
        if ((level < 0 || level > 100) && description != null) {
            Matcher matcher = NUMBER.matcher(description);
            while (matcher.find()) {
                int candidate = Integer.parseInt(matcher.group(1));
                if (candidate >= 0 && candidate <= 100) {
                    level = candidate;
                }
            }
        }
        if (description != null) {
            String text = description.toString().toLowerCase(Locale.ROOT);
            if (text.contains("正在充电") || text.contains("charging") && !text.contains("not")) {
                charging = true;
            } else if (text.contains("未充电") || text.contains("not charging")) {
                charging = false;
            }
        }
        if (level >= 0 && level <= 100) {
            lastBattery = level;
        }
        lastCharging = charging;
        return new BatteryState(lastBattery < 0 ? 0 : lastBattery, charging);
    }

    private static boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
            NetworkInfo wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifi != null && wifi.isConnected();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String currentTime(Context context) {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.is24HourFormat(context) ? "Hm" : "hma";
        String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
        return new SimpleDateFormat(pattern, locale).format(new Date());
    }

    private static boolean isVertical(ViewGroup target) {
        if (target instanceof LinearLayout
                && ((LinearLayout) target).getOrientation() == LinearLayout.VERTICAL) {
            return true;
        }
        return target.getWidth() > 0 && target.getHeight() > target.getWidth() * 1.25f;
    }

    private int id(String name) {
        return root.getResources().getIdentifier(name, "id", "com.android.systemui");
    }

    private View findFirstByName(View root, String name) {
        int id = id(name);
        return id == 0 ? null : root.findViewById(id);
    }

    private ImageView findImageByName(View root, String name) {
        List<View> views = new ArrayList<>();
        collectByName(root, name, views);
        ImageView fallback = null;
        for (View view : views) {
            if (view instanceof ImageView) {
                ImageView image = (ImageView) view;
                if (fallback == null) {
                    fallback = image;
                }
                if (image.getDrawable() != null) {
                    return image;
                }
            }
        }
        return fallback;
    }

    private void collectByName(View view, String name, List<View> output) {
        int id = id(name);
        if (id == 0) {
            return;
        }
        collectById(view, id, output);
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

    private static TextView asTextView(View view) {
        return view instanceof TextView ? (TextView) view : null;
    }

    private static ViewGroup asViewGroup(View view) {
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    private int dp(int value) {
        return Math.round(value * root.getResources().getDisplayMetrics().density);
    }

    private static final class LayoutSnapshot {
        final int height;
        final int minHeight;
        final boolean clipChildren;
        final boolean clipToPadding;

        private LayoutSnapshot(int height, int minHeight, boolean clipChildren, boolean clipToPadding) {
            this.height = height;
            this.minHeight = minHeight;
            this.clipChildren = clipChildren;
            this.clipToPadding = clipToPadding;
        }

        static LayoutSnapshot capture(ViewGroup group) {
            ViewGroup.LayoutParams params = group.getLayoutParams();
            return new LayoutSnapshot(
                    params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.height,
                    group.getMinimumHeight(), group.getClipChildren(), group.getClipToPadding());
        }

        void restore(ViewGroup group) {
            group.setMinimumHeight(minHeight);
            group.setClipChildren(clipChildren);
            group.setClipToPadding(clipToPadding);
            ViewGroup.LayoutParams params = group.getLayoutParams();
            if (params != null && params.height != height) {
                params.height = height;
                group.setLayoutParams(params);
            }
        }
    }

    private static final class BatteryState {
        final int level;
        final boolean charging;

        BatteryState(int level, boolean charging) {
            this.level = level;
            this.charging = charging;
        }
    }
}
