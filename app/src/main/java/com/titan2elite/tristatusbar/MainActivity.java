package com.titan2elite.tristatusbar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SettingsContract.PREFS_NAME, MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(18, 19, 22));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(36));
        scrollView.addView(content);

        TextView title = text("三合一状态栏", 28, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        content.addView(title);
        TextView subtitle = text("Titan 2 Elite · LSPosed", 14, Color.rgb(155, 160, 170));
        subtitle.setPadding(0, dp(6), 0, dp(20));
        content.addView(subtitle);

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(Color.rgb(31, 33, 38));
        preview.setPadding(dp(20), dp(18), dp(20), dp(18));
        TriStatusView indicator = new TriStatusView(this);
        indicator.render("09:34", Color.WHITE, getDrawable(R.drawable.ic_wifi), true, 78, true,
                prefs.getBoolean(SettingsContract.KEY_SHOW_BATTERY_PERCENT, false), 4, null);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.CENTER;
        preview.addView(indicator, indicatorParams);
        content.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sceneLabel = text("选择修改位置", 14, Color.rgb(142, 190, 255));
        sceneLabel.setPadding(0, dp(28), 0, dp(6));
        content.addView(sceneLabel);
        addSceneSwitch(content, Scene.STATUS, "普通状态栏", "主屏幕和应用内顶部状态栏");
        addSceneSwitch(content, Scene.SHADE, "通知与控制中心", "将左侧时钟合并到右侧系统图标区");
        addSceneSwitch(content, Scene.LOCK, "锁屏", "锁屏顶部状态区及左侧大时钟");

        TextView displayLabel = text("指示器显示", 14, Color.rgb(142, 190, 255));
        displayLabel.setPadding(0, dp(24), 0, dp(6));
        content.addView(displayLabel);
        addSwitch(content, SettingsContract.KEY_SHOW_BATTERY_PERCENT, false,
                "圆环内显示电量数字", "数字显示在圆环内上方，保留中央信号图标");

        TextView iconLabel = text("隐藏状态图标", 14, Color.rgb(142, 190, 255));
        iconLabel.setPadding(0, dp(24), 0, dp(6));
        content.addView(iconLabel);
        addSwitch(content, SettingsContract.KEY_HIDE_BLUETOOTH, false,
                "隐藏蓝牙图标", "只隐藏图标，不关闭蓝牙功能");
        addSwitch(content, SettingsContract.KEY_HIDE_RINGER, false,
                "隐藏静音 / 振动图标", "只隐藏图标，不改变声音模式");
        addSwitch(content, SettingsContract.KEY_HIDE_OTHER, false,
                "隐藏其他状态图标", "闹钟、热点、飞行模式等；保留时间、信号和电池");

        TextView restart = text("首次使用：在 LSPosed 中启用模块，作用域勾选 SystemUI，然后重启设备。", 14,
                Color.rgb(190, 195, 205));
        restart.setPadding(0, dp(24), 0, 0);
        content.addView(restart);

        if (isInstalled("io.github.kvmy666.duostatusbar")) {
            TextView conflict = text("检测到原版 Duo Status Bar。测试本模块前，请先在 LSPosed 中暂时停用原版，避免两个模块同时修改同一组图标。", 14,
                    Color.rgb(255, 190, 110));
            conflict.setPadding(0, dp(16), 0, 0);
            content.addView(conflict);
        }

        setContentView(scrollView);
    }

    private void addSceneSwitch(LinearLayout parent, Scene scene, String title, String summary) {
        addSwitch(parent, scene.key, scene.defaultEnabled, title, summary);
    }

    private void addSwitch(LinearLayout parent, String key, boolean defaultValue,
            String title, String summary) {
        Switch toggle = new Switch(this);
        toggle.setText(title + "\n" + summary);
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(16);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(15), 0, dp(15));
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).commit();
            getContentResolver().notifyChange(SettingsContract.CONTENT_URI, null);
        });
        parent.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
