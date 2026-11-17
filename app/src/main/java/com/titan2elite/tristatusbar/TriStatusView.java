package com.titan2elite.tristatusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact clock + signal + battery-ring indicator. */
public final class TriStatusView extends LinearLayout {
    private final TextView clockView;
    private final FrameLayout ringFrame;
    private final ImageView signalView;
    private final CellularView cellularView;
    private final TextView batteryView;
    private final CellDotsView dotsView;
    private final RingView ringView;
    private boolean vertical;
    private int ringSizePx = -1;
    private boolean largeClock;

    public TriStatusView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setGravity(Gravity.CENTER_VERTICAL);

        clockView = new TextView(context);
        clockView.setSingleLine(true);
        clockView.setIncludeFontPadding(false);
        clockView.setTextSize(14);
        clockView.setGravity(Gravity.CENTER);
        clockView.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        addView(clockView);

        ringFrame = new FrameLayout(context);
        ringFrame.setClipChildren(false);
        ringFrame.setClipToPadding(false);
        ringView = new RingView(context);
        ringFrame.addView(ringView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        signalView = new ImageView(context);
        signalView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int signalSize = dp(13);
        FrameLayout.LayoutParams signalParams = new FrameLayout.LayoutParams(
                signalSize, signalSize, Gravity.CENTER);
        ringFrame.addView(signalView, signalParams);

        cellularView = new CellularView(context);
        FrameLayout.LayoutParams cellularParams = new FrameLayout.LayoutParams(
                dp(18), dp(18), Gravity.CENTER);
        ringFrame.addView(cellularView, cellularParams);
        cellularView.setVisibility(GONE);

        batteryView = new TextView(context);
        batteryView.setSingleLine(true);
        batteryView.setIncludeFontPadding(false);
        batteryView.setTextSize(10);
        batteryView.setGravity(Gravity.CENTER);
        batteryView.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.BOLD));
        // Thin dark halo keeps the white number readable when it overlaps the
        // (white, when not charging) ring arc behind it.
        batteryView.setShadowLayer(
                getResources().getDisplayMetrics().density * 1.2f, 0, 0,
                Color.argb(190, 0, 0, 0));
        FrameLayout.LayoutParams batteryParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ringFrame.addView(batteryView, batteryParams);

        dotsView = new CellDotsView(context);
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(
                dp(20), dp(4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsParams.bottomMargin = dp(1);
        ringFrame.addView(dotsView, dotsParams);
        addView(ringFrame);

        // Force the first layout pass even though addView() has already created default params.
        vertical = true;
        setVerticalMode(false);
    }

    void setVerticalMode(boolean vertical) {
        if (this.vertical == vertical && getChildCount() == 2
                && ringFrame.getLayoutParams() != null) {
            return;
        }
        this.vertical = vertical;
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);

        LayoutParams clockParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (vertical) {
            clockParams.bottomMargin = dp(3);
        } else {
            clockParams.rightMargin = dp(6);
        }
        clockView.setLayoutParams(clockParams);
        applyClockSize();

        int ringSize = ringSizePx > 0 ? ringSizePx : dp(vertical ? 27 : 28);
        LayoutParams ringParams = new LayoutParams(ringSize, ringSize);
        ringFrame.setLayoutParams(ringParams);
        requestLayout();
    }

    void setRingSize(int px) {
        if (px <= 0 || px == ringSizePx) {
            return;
        }
        ringSizePx = px;
        ringFrame.setLayoutParams(new LayoutParams(px, px));
        requestLayout();
    }

    void setLargeClock(boolean large) {
        if (this.largeClock == large) {
            return;
        }
        this.largeClock = large;
        applyClockSize();
    }

    private void applyClockSize() {
        clockView.setTextSize(largeClock ? 28 : (vertical ? 13 : 14));
    }

    public void render(String time, int color, Drawable signal, boolean wifi,
            int batteryLevel, boolean charging, boolean showBatteryPercent, int mobileLevel,
            int mobileLevel2, String netLabel) {
        clockView.setText(time);
        clockView.setTextColor(color);

        Drawable copy = copyDrawable(signal);
        boolean compact = ringSizePx > 0 && ringSizePx < dp(24);
        boolean dual = mobileLevel2 >= 0;
        if (wifi) {
            signalView.setVisibility(copy != null ? VISIBLE : GONE);
            signalView.setImageDrawable(copy);
            signalView.setImageTintList(ColorStateList.valueOf(color));
            signalView.setTranslationY(showBatteryPercent ? dp(compact ? 2 : 2) : 0);
            cellularView.setVisibility(GONE);
        } else {
            signalView.setVisibility(GONE);
            cellularView.setState(mobileLevel, mobileLevel2, netLabel, color);
            // Keep the bars+label unit centred inside the ring; the battery
            // number lives in the top gap, the bars sit on the vertical centre.
            cellularView.setTranslationY(showBatteryPercent ? dp(compact ? 2 : 2) : 0);
            cellularView.setVisibility(VISIBLE);
            FrameLayout.LayoutParams cellLp = (FrameLayout.LayoutParams) cellularView.getLayoutParams();
            int cellHeight = dual ? dp(22) : dp(18);
            if (cellLp.height != cellHeight) {
                cellLp.height = cellHeight;
                cellularView.setLayoutParams(cellLp);
            }
        }
        batteryView.setText(String.valueOf(Math.max(0, Math.min(100, batteryLevel))));
        batteryView.setTextColor(color);
        batteryView.setTextSize(compact ? 8 : 10);
        batteryView.setTranslationY(showBatteryPercent ? -dp(compact ? 5 : 10) : 0);
        batteryView.setVisibility(showBatteryPercent ? VISIBLE : GONE);
        dotsView.setState(mobileLevel, mobileLevel2, color);
        FrameLayout.LayoutParams dotsLp = (FrameLayout.LayoutParams) dotsView.getLayoutParams();
        int dotsHeight = dual ? dp(8) : dp(4);
        if (dotsLp.height != dotsHeight) {
            dotsLp.height = dotsHeight;
            dotsView.setLayoutParams(dotsLp);
        }
        dotsView.setVisibility(showBatteryPercent && wifi ? VISIBLE : GONE);
        ringView.setState(batteryLevel, color, charging, showBatteryPercent);

        String network = wifi ? "WLAN" : "移动网络";
        setContentDescription(time + "，" + network + "，电量百分之" + batteryLevel
                + (charging ? "，正在充电" : ""));
    }

    private Drawable copyDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable : state.newDrawable(getResources()).mutate();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CellDotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int level;
        private int level2 = -1;
        private int color = Color.WHITE;

        CellDotsView(Context context) {
            super(context);
        }

        void setState(int level, int level2, int color) {
            this.level = Math.max(0, Math.min(4, level));
            this.level2 = level2 < 0 ? -1 : Math.max(0, Math.min(4, level2));
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float radius = density * 1.0f;
            float spacing = getWidth() / 4f;
            boolean dual = level2 >= 0;
            int rows = dual ? 2 : 1;
            for (int r = 0; r < rows; r++) {
                int lvl = r == 0 ? level : level2;
                float centerY = dual
                        ? (r == 0 ? getHeight() * 0.28f : getHeight() * 0.72f)
                        : getHeight() / 2f;
                for (int i = 0; i < 4; i++) {
                    paint.setColor(i < lvl ? color : Color.argb(65,
                            Color.red(color), Color.green(color), Color.blue(color)));
                    canvas.drawCircle(spacing * (i + 0.5f), centerY, radius, paint);
                }
            }
        }
    }

    /**
     * Cellular-mode glyph: four ascending rounded bars with the network
     * generation label ("5G"/"4G"/...) right underneath. Drawn as one compact
     * unit so bars and label never overlap; the whole unit is translated down
     * (see render) to stay clear of the battery number shown at the top of the
     * ring.
     */
    private static final class CellularView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF barRect = new RectF();
        private int level;
        private int level2 = -1;
        private String label = "5G";
        private int color = Color.WHITE;

        CellularView(Context context) {
            super(context);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
        }

        void setState(int level, int level2, String label, int color) {
            this.level = Math.max(0, Math.min(4, level));
            this.level2 = level2 < 0 ? -1 : Math.max(0, Math.min(4, level2));
            this.label = label == null || label.isEmpty() ? "5G" : label;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float barW = density * 2.0f;
            float gap = density * 1.6f;
            float pitch = barW + gap;
            float totalW = barW * 4f + gap * 3f;
            float startX = (w - totalW) / 2f;
            float radius = barW * 0.45f;
            if (level2 >= 0) {
                // Dual SIM: 8 dots (2 rows x 4), centred, with one shared label.
                float dotRadius = density * 1.2f;
                float spacingX = w / 4f;
                drawDotsRow(canvas, level, spacingX, density * 5.5f, dotRadius);
                drawDotsRow(canvas, level2, spacingX, density * 9.5f, dotRadius);
                textPaint.setColor(color);
                textPaint.setTextSize(density * 5.5f);
                canvas.drawText(label, w / 2f, density * 17f, textPaint);
            } else {
                float[] heights = {5.0f, 5.8f, 6.5f, 7.2f};
                drawBars(canvas, density, level, startX, pitch, barW, radius, heights,
                        density * 8.5f);
                textPaint.setColor(color);
                textPaint.setTextSize(density * 7f);
                canvas.drawText(label, w / 2f, density * 16.5f, textPaint);
            }
        }

        private void drawBars(Canvas canvas, float density, int lvl, float startX,
                float pitch, float barW, float radius, float[] heights, float baseY) {
            for (int i = 0; i < 4; i++) {
                boolean on = i < lvl;
                barPaint.setColor(on ? color : Color.argb(66,
                        Color.red(color), Color.green(color), Color.blue(color)));
                float bh = density * heights[i];
                float x = startX + i * pitch;
                barRect.set(x, baseY - bh, x + barW, baseY);
                canvas.drawRoundRect(barRect, radius, radius, barPaint);
            }
        }

        private void drawDotsRow(Canvas canvas, int lvl, float spacingX, float centerY,
                float radius) {
            for (int i = 0; i < 4; i++) {
                boolean on = i < lvl;
                barPaint.setColor(on ? color : Color.argb(66,
                        Color.red(color), Color.green(color), Color.blue(color)));
                canvas.drawCircle(spacingX * (i + 0.5f), centerY, radius, barPaint);
            }
        }
    }

    private static final class RingView extends View {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private int level;
        private int color = Color.WHITE;
        private boolean charging;
        private boolean percentOnTop;

        RingView(Context context) {
            super(context);
            basePaint.setStyle(Paint.Style.STROKE);
            basePaint.setStrokeCap(Paint.Cap.ROUND);
            levelPaint.setStyle(Paint.Style.STROKE);
            levelPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setState(int level, int color, boolean charging, boolean percentOnTop) {
            this.level = Math.max(0, Math.min(100, level));
            this.color = color;
            this.charging = charging;
            this.percentOnTop = percentOnTop;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float stroke = density * 2.2f;
            float inset = stroke / 2f + density;
            bounds.set(getPaddingLeft() + inset, getPaddingTop() + inset,
                    getWidth() - getPaddingRight() - inset,
                    getHeight() - getPaddingBottom() - inset);
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                return;
            }

            basePaint.setStrokeWidth(stroke);
            basePaint.setColor(Color.argb(70, Color.red(color), Color.green(color),
                    Color.blue(color)));
            int arcColor = charging ? Color.rgb(98, 217, 138)
                    : level <= 15 ? Color.rgb(255, 107, 107) : color;
            levelPaint.setStrokeWidth(stroke);
            levelPaint.setColor(arcColor);

            if (percentOnTop) {
                // DuoLauncher-style open track: top gap 240°~300° (number) +
                // bottom gap 33°~147° (signal). Left segment 147°~240°, right
                // segment 300°~33°. Fill runs clockwise from 147° and skips the
                // top gap, so the number stays clear of the arc.
                canvas.drawArc(bounds, 147f, 93f, false, basePaint);
                canvas.drawArc(bounds, 300f, 93f, false, basePaint);
                float span = 246f;
                float sweep = span * level / 100f;
                float s1 = Math.min(sweep, 93f);
                if (s1 > 0.6f) {
                    canvas.drawArc(bounds, 147f, s1, false, levelPaint);
                }
                if (sweep > 153f) {
                    float s2 = Math.min(sweep - 153f, 93f);
                    if (s2 > 0.6f) {
                        canvas.drawArc(bounds, 300f, s2, false, levelPaint);
                    }
                }
            } else {
                canvas.drawOval(bounds, basePaint);
                canvas.drawArc(bounds, -90f, level * 3.6f, false, levelPaint);
            }
        }
    }
}
