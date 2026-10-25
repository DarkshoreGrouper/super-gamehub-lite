package com.xj.winemu.sidebar;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Programmatic fourth sidebar page; compiled with the existing Java extension. */
public final class SuperDiagnostics {
    private static final String PANEL = "super.diagnostics.panel";
    private static final String TAB = "super.diagnostics.tab";
    private static final String OVERLAY = "super.diagnostics.overlay";
    private static final int CYAN = 0xff76e4ef;
    private SuperDiagnostics() {}

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
    private static View named(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }
    private static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity)c;
            Context next = ((ContextWrapper)c).getBaseContext();
            if (next == c) break;
            c = next;
        }
        return c instanceof Activity ? (Activity)c : null;
    }
    private static GradientDrawable background(int color, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(10);
        if (border) d.setStroke(2, CYAN);
        return d;
    }

    public static void onTouch(MotionEvent event) {
        if (SuperInputStats.enabled && event.getActionMasked() == MotionEvent.ACTION_MOVE)
            SuperInputStats.recordTouch(event.getHistorySize(), event.getPointerCount());
    }

    public static void bindDrawer(final View root) {
        try {
            if (root.findViewWithTag(TAB) != null) return;
            final View controls = named(root, "sidebar_opt");
            final View content = named(root, "layout_container");
            if (controls == null || !(controls.getParent() instanceof LinearLayout)
                || !(content instanceof FrameLayout)) {
                Log.e("SuperDiagnostics", "Sidebar containers unavailable");
                return;
            }
            final Context c = root.getContext();
            final Button tab = new Button(c);
            tab.setTag(TAB);
            tab.setText("SUPER");
            tab.setTextSize(10);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setTextColor(CYAN);
            tab.setPadding(0, 0, 0, 0);
            tab.setMinWidth(0);
            tab.setMinimumWidth(0);
            tab.setContentDescription("Super Gamehub Lite diagnostics tab");
            tab.setBackground(background(0xff142238, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c,48), dp(c,48));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(c,16);
            ((LinearLayout)controls.getParent()).addView(tab, 3, lp);

            final DiagnosticsPanel panel = new DiagnosticsPanel(c);
            panel.setTag(PANEL);
            panel.setVisibility(View.GONE);
            panel.setElevation(dp(c,8));
            ((FrameLayout)content).addView(panel, new FrameLayout.LayoutParams(-1,-1));
            tab.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (String name : new String[]{"sidebar_opt","sidebar_performance","sidebar_setting"}) {
                        View old = named(root,name);
                        if (old != null) old.setAlpha(0.45f);
                    }
                    tab.setBackground(background(0xff20364d, true));
                    panel.setVisibility(View.VISIBLE);
                    panel.bringToFront();
                    panel.refresh();
                }
            });
        } catch (RuntimeException e) {
            Log.e("SuperDiagnostics", "Could not attach diagnostics tab", e);
        }
    }

    /** Called by native sidebar selection without replacing its click listeners. */
    public static void hideDrawer(View root) {
        View panel = root.findViewWithTag(PANEL);
        if (panel == null || panel.getVisibility() != View.VISIBLE) return;
        panel.setVisibility(View.GONE);
        View tab = root.findViewWithTag(TAB);
        if (tab != null) tab.setBackground(background(0xff142238, false));
        for (String name : new String[]{"sidebar_opt","sidebar_performance","sidebar_setting"}) {
            View old = named(root,name);
            if (old != null) old.setAlpha(1f);
        }
    }

    public static void stop() { SuperInputStats.setEnabled(false); }

    private static TextView text(Context c, String value, int size) {
        TextView t = new TextView(c);
        t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setPadding(0,dp(c,6),0,dp(c,6));
        return t;
    }

    private static final class DiagnosticsPanel extends ScrollView implements Runnable {
        private final TextView counters;
        private final Switch toggle;
        DiagnosticsPanel(final Context c) {
            super(c);
            setBackgroundColor(0xff101b2e);
            setFillViewport(true);
            LinearLayout column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));
            addView(column, new ScrollView.LayoutParams(-1,-2));
            TextView title = text(c,"Super Gamehub Lite",20);
            title.setTextColor(CYAN); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            column.addView(title);
            column.addView(text(c,"Mouse Input Diagnostics",16));
            toggle = new Switch(c);
            toggle.setText("Record + show overlay"); toggle.setTextColor(Color.WHITE);
            toggle.setChecked(SuperInputStats.enabled);
            column.addView(toggle,new LinearLayout.LayoutParams(-1,dp(c,52)));
            counters = text(c,"",13);
            counters.setTypeface(Typeface.MONOSPACE);
            column.addView(counters);
            Button reset = new Button(c); reset.setText("Reset measurements");
            column.addView(reset,new LinearLayout.LayoutParams(-1,-2));
            Button copy = new Button(c); copy.setText("Copy report");
            column.addView(copy,new LinearLayout.LayoutParams(-1,-2));
            column.addView(text(c,"Enable, close the sidebar, then compare standing still, running straight and camera swipes. Reset before each test.\n\nCounts include all touch movement, including controls and menu gestures. Send time measures the native call, not total Wine or game latency. Input is unchanged.",12));
            toggle.setOnCheckedChangeListener((button,checked) -> {
                SuperInputStats.setEnabled(checked);
                updateOverlay(c);
                refresh();
            });
            reset.setOnClickListener(v -> { SuperInputStats.reset(); refresh(); });
            copy.setOnClickListener(v -> {
                String version = "unknown";
                try { version = c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; }
                catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
                String report = SuperInputStats.report() + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                    + "\nAndroid: " + Build.VERSION.RELEASE + "\nApp: " + c.getPackageName() + " " + version;
                ClipboardManager clipboard = (ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Super Gamehub Lite diagnostics",report));
                    Toast.makeText(c,"Diagnostics report copied",Toast.LENGTH_SHORT).show();
                }
            });
            refresh();
        }
        void refresh() {
            if (toggle.isChecked() != SuperInputStats.enabled) toggle.setChecked(SuperInputStats.enabled);
            counters.setText((SuperInputStats.enabled ? "RECORDING\n" : "STOPPED (last session)\n") + SuperInputStats.summary());
        }
        public void run() { if (isShown()) refresh(); postDelayed(this,1000); }
        protected void onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(this); post(this); }
        protected void onDetachedFromWindow() { removeCallbacks(this); super.onDetachedFromWindow(); }
    }

    private static void updateOverlay(Context c) {
        Activity a = activity(c);
        if (a == null) return;
        ViewGroup content = a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        DiagnosticsOverlay overlay = content.findViewWithTag(OVERLAY);
        if (overlay == null && SuperInputStats.enabled) {
            overlay = new DiagnosticsOverlay(a);
            overlay.setTag(OVERLAY);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
            lp.topMargin=dp(c,8); lp.rightMargin=dp(c,8);
            content.addView(overlay,lp);
        }
        if (overlay != null) overlay.start();
    }

    private static final class DiagnosticsOverlay extends TextView implements Runnable {
        DiagnosticsOverlay(Context c) {
            super(c);
            setTextColor(CYAN); setTextSize(11); setTypeface(Typeface.MONOSPACE);
            setBackground(background(0xdd07101d,false));
            setPadding(dp(c,8),dp(c,5),dp(c,8),dp(c,5));
            setClickable(false); setFocusable(false); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setElevation(dp(c,20));
        }
        void start() { removeCallbacks(this); run(); }
        public void run() {
            if (!SuperInputStats.enabled) { setVisibility(View.GONE); return; }
            setVisibility(View.VISIBLE);
            setText("SUPER INPUT\n" + SuperInputStats.summary());
            postDelayed(this,1000);
        }
        protected void onDetachedFromWindow() { removeCallbacks(this); super.onDetachedFromWindow(); }
    }
}
