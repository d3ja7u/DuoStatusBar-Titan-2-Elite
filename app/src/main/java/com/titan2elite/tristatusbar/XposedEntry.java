package com.titan2elite.tristatusbar;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedEntry implements IXposedHookLoadPackage {
    private static final String TAG = "DuoStatusBar: ";
    private static final Map<View, SceneController> CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, LockClockController> LOCK_CLOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + "handleLoadPackage SystemUI branch entered");

        hookView(lpparam.classLoader,
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                Scene.STATUS);
        hookNotificationPanel(lpparam.classLoader);
        hookKeyguardClock(lpparam.classLoader);
        hookView(lpparam.classLoader,
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                Scene.LOCK);

        // This second entry point runs after SystemUI has bound its final visibility state.
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                    lpparam.classLoader, "onViewCreated", View.class, Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args[0] instanceof View) {
                                attach(Scene.STATUS, (View) param.args[0]);
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "CollapsedStatusBarFragment hook skipped: " + throwable);
        }
    }

    private static void hookView(ClassLoader classLoader, String className, Scene scene) {
        try {
            XposedHelpers.findAndHookMethod(className, classLoader, "onFinishInflate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                attach(scene, (View) param.thisObject);
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + className + " hook skipped: " + throwable);
        }
    }

    private static void hookNotificationPanel(ClassLoader classLoader) {
        try {
            // This MtkSystemUI version does not override onFinishInflate. View.post waits
            // until inflation and attachment have completed, so the split-shade children exist.
            XposedHelpers.findAndHookConstructor(
                    "com.android.systemui.shade.NotificationPanelView", classLoader,
                    Context.class, AttributeSet.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View root = (View) param.thisObject;
                                root.post(() -> {
                                    attach(Scene.SHADE, root);
                                    attachLockClock(root);
                                });
                            }
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "NotificationPanelView hook skipped: " + throwable);
        }
    }

    private static void hookKeyguardClock(ClassLoader classLoader) {
        String[] binders = {
                "com.android.systemui.keyguard.ui.binder.KeyguardRootViewBinder",
                "com.android.systemui.keyguard.ui.binder.KeyguardClockViewBinder"
        };
        for (String className : binders) {
            try {
                Class<?> binder = XposedHelpers.findClass(className, classLoader);
                XposedBridge.hookAllMethods(binder, "bind", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        for (Object arg : param.args) {
                            if (arg instanceof View) {
                                // ClockSection may add its clock after bind returns; the
                                // controller keeps resolving the clock on subsequent ticks.
                                attachLockClock((View) arg);
                                break;
                            }
                        }
                    }
                });
            } catch (Throwable throwable) {
                XposedBridge.log(TAG + className + " hook skipped: " + throwable);
            }
        }
    }

    private static boolean hasLockClock(View view) {
        int regular = view.getResources().getIdentifier(
                "lockscreen_clock_view", "id", "com.android.systemui");
        int large = view.getResources().getIdentifier(
                "lockscreen_clock_view_large", "id", "com.android.systemui");
        return (regular != 0 && view.findViewById(regular) != null)
                || (large != 0 && view.findViewById(large) != null);
    }

    private static void attach(Scene scene, View root) {
        synchronized (CONTROLLERS) {
            SceneController controller = CONTROLLERS.get(root);
            if (controller == null) {
                controller = new SceneController(scene, root, SettingsBridge.get(root.getContext()));
                CONTROLLERS.put(root, controller);
            }
            controller.apply();
            root.post(controller::apply);
        }
    }

    private static void attachLockClock(View root) {
        synchronized (LOCK_CLOCKS) {
            LockClockController controller = LOCK_CLOCKS.get(root);
            if (controller == null) {
                controller = new LockClockController(root, SettingsBridge.get(root.getContext()));
                LOCK_CLOCKS.put(root, controller);
            }
            controller.apply();
            root.post(controller::apply);
        }
    }
}
