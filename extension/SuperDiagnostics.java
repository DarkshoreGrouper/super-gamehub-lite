package com.xj.winemu.sidebar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.AttributeSet;
import java.lang.reflect.Proxy;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.DateFormat;
import java.util.Date;

/** Programmatic fourth sidebar page; compiled with the existing Java extension. */
public final class SuperDiagnostics {
    private static final String PANEL = "super.diagnostics.panel";
    private static final String TAB = "super.diagnostics.tab";
    private static final String OVERLAY = "super.diagnostics.overlay";
    private static final int CYAN = 0xff76e4ef;
    private static volatile boolean hudEnabled;
    private SuperDiagnostics() {}
    public static void configureHud(Context c) {
        hudEnabled=c.getSharedPreferences("super_diagnostics",Context.MODE_PRIVATE).getBoolean("diagnostics_hud",false);
        SuperInputStats.setLiveEnabled(hudEnabled);
        SuperCaptureProbe.setEnabled(c,c.getSharedPreferences("super_diagnostics",Context.MODE_PRIVATE).getBoolean("native_camera_probe",false));
    }

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
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) SuperCameraFrameGen.endGesture();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
            || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_DOWN
            || action == MotionEvent.ACTION_POINTER_DOWN) SuperMouseInput.flush();
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

    /** Called only when the native sidebar navigates to another page.
     * Focus changes inside native radio lists must never hide the SUPER page.
     */
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

    public static void stop() {
        SuperCaptureProbe.stop();
        SuperCameraFrameGen.stopGame();
        SuperMouseInput.setEnabled(false);
        SuperInputStats.setEnabled(false);
        SuperInputStats.setLiveEnabled(false);
    }

    private static TextView text(Context c, String value, int size) {
        TextView t = new TextView(c);
        t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setPadding(0,dp(c,6),0,dp(c,6));
        return t;
    }

    /** Reuse the app's own sidebar rows, assets and focus behavior. Reflection is UI-only. */
    private static View sidebarRow(Context c, String className) {
        try {
            return (View)Class.forName(className,true,c.getClassLoader())
                .getConstructor(Context.class,AttributeSet.class).newInstance(c,null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Native sidebar row unavailable: " + className,e);
        }
    }

    private interface ChoiceListener { void changed(int selection); }
    private static java.util.List<Object> menuItems(Context c, String[] labels, int selection)
            throws ReflectiveOperationException {
        java.lang.reflect.Constructor<?> constructor = Class.forName("com.xj.winemu.bean.CollapseItemMenu",true,c.getClassLoader())
            .getConstructor(String.class,boolean.class);
        java.util.List<Object> items = new java.util.ArrayList<>();
        for (int i=0; i<labels.length; i++) items.add(constructor.newInstance(labels[i],i==selection));
        return items;
    }
    private static View choiceList(Context c, String title, String[] labels, int selection, ChoiceListener listener) {
        final View row = sidebarRow(c,"com.xj.winemu.view.CollapseMenuSettingView");
        try {
            java.lang.reflect.Method items = row.getClass().getMethod("E",java.util.List.class);
            java.lang.reflect.Method heading = row.getClass().getMethod("G",String.class);
            items.invoke(row,menuItems(c,labels,selection));
            heading.invoke(row,title + ": " + labels[selection]);
            Class<?> function = Class.forName("kotlin.jvm.functions.Function2",true,c.getClassLoader());
            Object callback = Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args) -> {
                if (method.getName().equals("invoke")) {
                    int index = ((Number)args[0]).intValue();
                    if (index >= 0 && index < labels.length) {
                        listener.changed(index);
                        items.invoke(row,menuItems(c,labels,index));
                        heading.invoke(row,title + ": " + labels[index]);
                    }
                    return null;
                }
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return "Super radio selection listener";
            });
            row.getClass().getMethod("F",function).invoke(row,callback);
            return row;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not configure native radio list",e);
        }
    }

    private static final class DiagnosticsPanel extends ScrollView implements Runnable {
        private final TextView counters;
        private final Switch toggle;
        private final Switch batchToggle;
        private final Switch hudToggle;
        private final Switch cameraToggle;
        private final TextView cameraStatus;
        private final Button learnTrigger;
        private final Button resetTrigger;
        private final View reportsHeader;
        private final View reportsArrow;
        private final LinearLayout reports;
        private final TextView savedList;
        private final EditText label;
        private final SharedPreferences preferences;
        private JSONArray saved;
        DiagnosticsPanel(final Context c) {
            super(c);
            preferences = c.getApplicationContext().getSharedPreferences("super_diagnostics", Context.MODE_PRIVATE);
            configureHud(c);
            int storedRate = preferences.getInt("batching_rate_hz",SuperMouseBatcher.DEFAULT_RATE_HZ);
            SuperMouseInput.setRateHz(SuperMouseBatcher.isSupportedRateHz(storedRate) ? storedRate : SuperMouseBatcher.DEFAULT_RATE_HZ);
            SuperMouseInput.setFixedPacing(preferences.getBoolean("batching_fixed_pacing",false));
            try { saved = new JSONArray(preferences.getString("reports", "[]")); }
            catch (JSONException e) { saved = new JSONArray(); }
            setBackgroundColor(0xff101b2e);
            setFillViewport(true);
            LinearLayout column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));
            addView(column, new ScrollView.LayoutParams(-1,-2));
            TextView title = text(c,"Super Gamehub Lite",20);
            title.setTextColor(CYAN); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            column.addView(title);
            hudToggle=new Switch(c);
            hudToggle.setText("Diagnostics HUD"); hudToggle.setTextColor(Color.WHITE); hudToggle.setChecked(hudEnabled);
            column.addView(hudToggle,new LinearLayout.LayoutParams(-1,dp(c,52)));
            hudToggle.setOnCheckedChangeListener((button,checked) -> {
                hudEnabled=checked; SuperInputStats.setLiveEnabled(checked);
                preferences.edit().putBoolean("diagnostics_hud",checked).apply(); updateOverlay(c);
            });
            Switch captureProbe=new Switch(c);
            captureProbe.setText("Native camera-state probe (experimental)");
            captureProbe.setTextColor(Color.WHITE); captureProbe.setChecked(SuperCaptureProbe.isEnabled());
            column.addView(captureProbe,new LinearLayout.LayoutParams(-1,dp(c,64)));
            captureProbe.setOnCheckedChangeListener((button,checked) -> {
                preferences.edit().putBoolean("native_camera_probe",checked).apply();
                SuperCaptureProbe.setEnabled(c,checked);
            });
            column.addView(text(c,"Enable Diagnostics HUD to compare X11 grab, release, recenter and cursor-image signals. Observe camera mode, then right-click for cursor mode, then walk to resume camera. Probe does not change batching or frame generation. Counts start when enabled; enable before switching modes. Adds diagnostic overhead; turn off afterward.",12));
            batchToggle = new Switch(c);
            batchToggle.setText("Experimental mouse batching");
            batchToggle.setTextColor(Color.WHITE);
            batchToggle.setChecked(SuperMouseInput.isEnabled());
            column.addView(batchToggle,new LinearLayout.LayoutParams(-1,dp(c,64)));
            final int[] rates = {40,45,50,55,60,75,90,100};
            int selectedRate = 4;
            for (int i=0;i<rates.length;i++) if (rates[i]==SuperMouseInput.getRateHz()) selectedRate=i;
            column.addView(choiceList(c,"Batching rate",new String[]{"40 Hz","45 Hz","50 Hz","55 Hz","60 Hz","75 Hz","90 Hz","100 Hz"},
                selectedRate,index -> {
                    SuperMouseInput.setRateHz(rates[index]);
                    preferences.edit().putInt("batching_rate_hz",rates[index]).apply();
                }),new LinearLayout.LayoutParams(-1,-2));
            column.addView(choiceList(c,"Mouse delivery timing",new String[]{"Batch windows (original)","Fixed pacing (experimental)"},
                SuperMouseInput.isFixedPacing()?1:0,index -> {
                    SuperMouseInput.setFixedPacing(index==1);
                    preferences.edit().putBoolean("batching_fixed_pacing",index==1).apply();
                }),new LinearLayout.LayoutParams(-1,-2));
            column.addView(text(c,"Batching starts enabled by default. Toggle, rate and timing choices are remembered. Fixed pacing sends pending movement on a regular schedule; idle ticks send nothing. Android scheduling may delay delivery.",12));
            cameraToggle = new Switch(c);
            cameraToggle.setText("Frame generation on camera-look gestures");
            cameraToggle.setTextColor(Color.WHITE);
            cameraToggle.setChecked(SuperCameraFrameGen.isEnabled());
            column.addView(cameraToggle,new LinearLayout.LayoutParams(-1,dp(c,64)));
            final BhFrameGenSettings.Preset[] presets = BhFrameGenSettings.Preset.values();
            String[] presetLabels = new String[presets.length];
            for (int i=0; i<presets.length; i++) presetLabels[i] = presets[i].getLabel(c);
            column.addView(choiceList(c,"Camera frame-gen preset",presetLabels,
                SuperCameraFrameGen.selectedPreset(c).ordinal(),index -> SuperCameraFrameGen.setPreset(c,presets[index])),
                new LinearLayout.LayoutParams(-1,-2));
            final SuperCameraMotion.Trigger[] triggers = SuperCameraMotion.Trigger.values();
            String[] triggerLabels = {"Right mouse held + movement","Left mouse held + movement","Middle mouse held + movement",
                "Left or right mouse held + movement","Learned key held + movement","Learned key toggle + movement"};
            column.addView(choiceList(c,"Frame-gen trigger",triggerLabels,SuperCameraFrameGen.selectedTrigger(c).ordinal(),
                index -> { SuperCameraFrameGen.setTrigger(c,triggers[index]); refresh(); }),new LinearLayout.LayoutParams(-1,-2));
            learnTrigger = new Button(c);
            learnTrigger.setOnClickListener(v -> {
                if (SuperCameraFrameGen.isLearning()) SuperCameraFrameGen.cancelLearning();
                else {
                    SuperCameraFrameGen.learnKey();
                    Toast.makeText(c,"Close the sidebar and press your mapped game key or keyboard key. Input is still sent to the game.",Toast.LENGTH_LONG).show();
                }
                refresh();
            });
            column.addView(learnTrigger,new LinearLayout.LayoutParams(-1,-2));
            resetTrigger = new Button(c); resetTrigger.setText("Reset learned toggle state to OFF");
            resetTrigger.setOnClickListener(v -> { SuperCameraFrameGen.resetTrigger(); refresh(); });
            column.addView(resetTrigger,new LinearLayout.LayoutParams(-1,-2));
            cameraStatus = text(c,"",12);
            column.addView(cameraStatus);
            column.addView(text(c,"Choose the button/key your game's camera uses. A latched mouse button counts as held. Key-toggle mode follows each press; reset its state if the game and trigger get out of sync. Left-button item dragging is suppressed except in explicit left-button trigger modes. Walking is not required. Cursor movement alone stays off. Stops on release or after 100 ms without movement. This detects input, not the game's camera angle. Requires GameScopeVK. Turning this feature off restores Performance-tab frame-generation settings.",12));
            cameraToggle.setOnCheckedChangeListener((button,checked) -> {
                if (checked && !SuperCameraFrameGen.supported(c)) {
                    Toast.makeText(c,"Camera frame generation requires the GameScopeVK driver",Toast.LENGTH_LONG).show();
                    button.setChecked(false);
                    return;
                }
                SuperCameraFrameGen.setEnabled(c,checked);
                refresh();
            });
            reportsHeader = sidebarRow(c,"com.xj.winemu.view.SidebarClickItemView");
            reportsArrow = named(reportsHeader,"iv_icon");
            ((TextView)named(reportsHeader,"tv_name")).setText("Reports");
            column.addView(reportsHeader,new LinearLayout.LayoutParams(-1,dp(c,56)));
            reports = new LinearLayout(c);
            reports.setOrientation(LinearLayout.VERTICAL);
            int bodyBackground = c.getResources().getIdentifier("winemu_bg_mapping_rv","drawable",c.getPackageName());
            if (bodyBackground != 0) reports.setBackgroundResource(bodyBackground);
            reports.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));
            reports.setVisibility(preferences.getBoolean("reports_expanded",false) ? View.VISIBLE : View.GONE);
            if (reportsArrow != null) reportsArrow.setRotation(reports.getVisibility() == View.VISIBLE ? 270f : 90f);
            LinearLayout.LayoutParams reportsLayout = new LinearLayout.LayoutParams(-1,-2);
            reportsLayout.bottomMargin = dp(c,8);
            column.addView(reports,reportsLayout);
            reportsHeader.setOnClickListener(v -> {
                boolean expanded = reports.getVisibility() != View.VISIBLE;
                reports.setVisibility(expanded ? View.VISIBLE : View.GONE);
                if (reportsArrow != null) reportsArrow.animate().rotation(expanded ? 270f : 90f).setDuration(300).start();
                preferences.edit().putBoolean("reports_expanded",expanded).apply();
                refresh();
            });
            reports.addView(text(c,"Mouse Input Diagnostics",16));
            toggle = new Switch(c);
            toggle.setText("Record + show overlay"); toggle.setTextColor(Color.WHITE);
            toggle.setChecked(SuperInputStats.enabled);
            reports.addView(toggle,new LinearLayout.LayoutParams(-1,dp(c,52)));
            counters = text(c,"",13);
            counters.setTypeface(Typeface.MONOSPACE);
            reports.addView(counters);
            Button reset = new Button(c); reset.setText("Reset measurements");
            reports.addView(reset,new LinearLayout.LayoutParams(-1,-2));
            Button copy = new Button(c); copy.setText("Copy report");
            reports.addView(copy,new LinearLayout.LayoutParams(-1,-2));
            label = new EditText(c);
            label.setTextColor(Color.WHITE);
            label.setHintTextColor(0xffb7c5d8);
            label.setHint("Test label / observed FPS (optional)");
            label.setMaxLines(3);
            reports.addView(label,new LinearLayout.LayoutParams(-1,-2));
            Button save = new Button(c); save.setText("Stop + save report");
            reports.addView(save,new LinearLayout.LayoutParams(-1,-2));
            savedList = text(c,"",12);
            reports.addView(savedList);
            Button copyAll = new Button(c); copyAll.setText("Copy all saved reports");
            reports.addView(copyAll,new LinearLayout.LayoutParams(-1,-2));
            Button clear = new Button(c); clear.setText("Clear saved reports");
            reports.addView(clear,new LinearLayout.LayoutParams(-1,-2));
            reports.addView(text(c,"Choose batching ON or OFF before each test. Record, add a label/FPS, then Stop + save report. Enable recording again for a fresh test. Copy all saved reports when finished. Saved reports survive app updates. Collapsing Reports keeps recording active.\n\nNative movement counts are actual forwarded calls; incoming counts show the original traffic. Send time measures the native call, not total Wine or game latency.",12));
            batchToggle.setOnCheckedChangeListener((button,checked) -> {
                SuperMouseInput.setEnabled(checked);
                preferences.edit().putBoolean("batching_enabled",checked).apply();
                refresh();
            });
            toggle.setOnCheckedChangeListener((button,checked) -> {
                SuperMouseInput.flush();
                SuperInputStats.setEnabled(checked);
                updateOverlay(c);
                refresh();
            });
            reset.setOnClickListener(v -> { SuperMouseInput.flush(); SuperInputStats.reset(); refresh(); });
            copy.setOnClickListener(v -> copyReport(c,currentReport(c)));
            save.setOnClickListener(v -> {
                SuperMouseInput.flush();
                SuperInputStats.setEnabled(false);
                updateOverlay(c);
                String name = label.getText().toString().trim();
                if (name.isEmpty()) name = "Test " + (saved.length() + 1);
                String report = "Test: " + name + "\nSaved: " + DateFormat.getDateTimeInstance().format(new Date())
                    + "\n\n" + currentReport(c);
                // Save a new array first so a failed write does not alter the displayed collection.
                try {
                    JSONArray next = new JSONArray(saved.toString());
                    next.put(report);
                    if (preferences.edit().putString("reports",next.toString()).commit()) {
                        saved = next;
                        label.setText("");
                        Toast.makeText(c,"Report saved",Toast.LENGTH_SHORT).show();
                    } else Toast.makeText(c,"Could not save report; current report is still available",Toast.LENGTH_LONG).show();
                } catch (JSONException e) { Log.e("SuperDiagnostics","Could not save report",e); }
                refresh();
            });
            copyAll.setOnClickListener(v -> {
                if (saved.length() == 0) {
                    Toast.makeText(c,"No saved reports yet",Toast.LENGTH_SHORT).show();
                    return;
                }
                StringBuilder reports = new StringBuilder("Super Gamehub Lite - Saved diagnostics reports (" + saved.length() + ")\n");
                for (int i=0; i<saved.length(); i++)
                    reports.append("\n========== Report ").append(i+1).append(" ==========\n").append(saved.optString(i)).append("\n");
                copyReport(c,reports.toString());
            });
            clear.setOnClickListener(v -> new AlertDialog.Builder(c)
                .setTitle("Clear saved reports?")
                .setMessage("This deletes the saved reports on this device. The current session is kept.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Clear",(dialog,which) -> {
                    if (preferences.edit().remove("reports").commit()) { saved = new JSONArray(); refresh(); }
                    else Toast.makeText(c,"Could not clear reports",Toast.LENGTH_SHORT).show();
                }).show());
            refresh();
        }
        void refresh() {
            if (cameraToggle.isChecked() != SuperCameraFrameGen.isEnabled()) cameraToggle.setChecked(SuperCameraFrameGen.isEnabled());
            boolean keyTrigger = SuperCameraFrameGen.selectedTrigger(getContext()) == SuperCameraMotion.Trigger.KEY_HELD
                || SuperCameraFrameGen.selectedTrigger(getContext()) == SuperCameraMotion.Trigger.KEY_TOGGLE;
            learnTrigger.setVisibility(keyTrigger ? View.VISIBLE : View.GONE);
            resetTrigger.setVisibility(SuperCameraFrameGen.selectedTrigger(getContext()) == SuperCameraMotion.Trigger.KEY_TOGGLE ? View.VISIBLE : View.GONE);
            learnTrigger.setText(SuperCameraFrameGen.isLearning() ? "Cancel learning" : "Learn trigger key (" + SuperCameraFrameGen.keyLabel(getContext()) + ")");
            cameraStatus.setText(SuperCameraFrameGen.hudSummary()
                + (SuperCameraFrameGen.supported(getContext()) ? "" : " · GameScopeVK unavailable"));
            if (batchToggle.isChecked() != SuperMouseInput.isEnabled()) batchToggle.setChecked(SuperMouseInput.isEnabled());
            TextView reportDescription = (TextView)named(reportsHeader,"tv_desc");
            reportDescription.setText(saved.length() + " saved" + (SuperInputStats.enabled ? " · recording" : ""));
            reportDescription.setVisibility(View.VISIBLE);
            reportsHeader.setContentDescription("Reports, " + saved.length() + " saved, "
                + (reports.getVisibility() == View.VISIBLE ? "expanded" : "collapsed")
                + (SuperInputStats.enabled ? ", recording" : ""));
            if (toggle.isChecked() != SuperInputStats.enabled) toggle.setChecked(SuperInputStats.enabled);
            counters.setText((SuperInputStats.enabled ? "RECORDING\n" : "STOPPED (last session)\n") + SuperInputStats.summary());
            StringBuilder list = new StringBuilder("Saved reports: " + saved.length());
            for (int i=0; i<saved.length(); i++) {
                String report = saved.optString(i);
                int end = report.indexOf("\n\n");
                list.append("\n\n").append(i+1).append(". ").append(end < 0 ? report : report.substring(0,end));
            }
            savedList.setText(list.toString());
        }
        public void run() { if (isShown()) refresh(); postDelayed(this,1000); }
        protected void onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(this); post(this); post(() -> updateOverlay(getContext())); }
        protected void onDetachedFromWindow() { removeCallbacks(this); super.onDetachedFromWindow(); }
    }

    private static String currentReport(Context c) {
        String version = "unknown";
        try { version = c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        return SuperInputStats.report() + "\n"+SuperMouseInput.liveSummary()+"\n" + SuperCameraFrameGen.summary(c)+"\n"+SuperCaptureProbe.summary() + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
            + "\nAndroid: " + Build.VERSION.RELEASE + "\nApp: " + c.getPackageName() + " " + version;
    }

    private static void copyReport(Context c, String report) {
        ClipboardManager clipboard = (ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            try {
                clipboard.setPrimaryClip(ClipData.newPlainText("Super Gamehub Lite diagnostics",report));
                Toast.makeText(c,"Diagnostics copied",Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                Toast.makeText(c,"Could not copy reports; saved reports are kept",Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void updateOverlay(Context c) {
        Activity a = activity(c);
        if (a == null) return;
        ViewGroup content = a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        DiagnosticsOverlay overlay = content.findViewWithTag(OVERLAY);
        if (overlay == null && (SuperInputStats.enabled || hudEnabled)) {
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
            if (!SuperInputStats.enabled && !hudEnabled) { setVisibility(View.GONE); return; }
            setVisibility(View.VISIBLE);
            setText("SUPER INPUT\n" + (hudEnabled ? SuperMouseInput.liveSummary()+"\n"+SuperCameraFrameGen.hudSummary()+(SuperCaptureProbe.isEnabled()?"\n"+SuperCaptureProbe.hudSummary():"") : SuperInputStats.summary()));
            postDelayed(this,hudEnabled ? 200 : 1000);
        }
        protected void onDetachedFromWindow() { removeCallbacks(this); super.onDetachedFromWindow(); }
    }
}
