package com.modhost.api;

import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Small "Mods" button + panel drawn over the game: toggle Java mods and read the log. */
final class MenuUi {
    private static Activity host;
    private static Button button;
    private static LinearLayout panel, list;
    private static TextView logView;

    private MenuUi() {}

    static void attach(final Activity a) {
        a.runOnUiThread(new Runnable() {
            public void run() {
                if (host == a && button != null && button.getParent() != null) return;
                remove();
                host = a;

                button = new Button(a);
                button.setText("Mods");
                button.setTextSize(12);
                button.setAllCaps(false);
                button.setTextColor(Color.WHITE);
                button.setBackgroundColor(0xAA000000);
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { toggle(a); }
                });
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                bp.setMargins(0, dp(a, 48), dp(a, 8), 0);
                a.addContentView(button, bp);

                panel = buildPanel(a);
                panel.setVisibility(View.GONE);
                a.addContentView(panel, new FrameLayout.LayoutParams(
                        dp(a, 320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            }
        });
    }

    static void remove() {
        View[] views = { button, panel };
        for (View v : views) {
            if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        }
        button = null; panel = null; list = null; logView = null; host = null;
    }

    private static void toggle(Activity a) {
        if (panel == null) return;
        if (panel.getVisibility() == View.VISIBLE) {
            panel.setVisibility(View.GONE);
        } else {
            refresh(a);
            panel.setVisibility(View.VISIBLE);
        }
    }

    private static LinearLayout buildPanel(final Activity a) {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(0xF0202020);
        int pad = dp(a, 12);
        p.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(a);
        title.setText("Java mods");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        p.addView(title);

        ScrollView modsScroll = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        modsScroll.addView(list);
        p.addView(modsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 150)));

        TextView logTitle = new TextView(a);
        logTitle.setText("Log");
        logTitle.setTextColor(0xFFAAAAAA);
        logTitle.setTextSize(12);
        p.addView(logTitle);

        ScrollView logScroll = new ScrollView(a);
        logView = new TextView(a);
        logView.setTextColor(0xFFCCCCCC);
        logView.setTextSize(10);
        logScroll.addView(logView);
        p.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 110)));

        Button close = new Button(a);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { panel.setVisibility(View.GONE); }
        });
        p.addView(close);
        return p;
    }

    private static void refresh(Activity a) {
        if (list == null) return;
        list.removeAllViews();
        List<Bootstrap.Entry> mods = Bootstrap.list();
        if (mods.isEmpty()) {
            TextView none = new TextView(a);
            none.setText("No Java mods loaded");
            none.setTextColor(0xFFAAAAAA);
            list.addView(none);
        }
        for (final Bootstrap.Entry e : mods) {
            CheckBox cb = new CheckBox(a);
            cb.setText(e.id + (e.fabric ? "  (Fabric-style)" : ""));
            cb.setTextColor(Color.WHITE);
            cb.setChecked(e.enabled);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    Bootstrap.setEnabled(e.id, on);
                    refreshLog();
                }
            });
            list.addView(cb);
        }
        refreshLog();
    }

    private static void refreshLog() {
        if (logView == null) return;
        StringBuilder sb = new StringBuilder();
        for (String l : ModLogger.recent(14)) sb.append(l).append('\n');
        logView.setText(sb.toString());
    }

    private static int dp(Activity a, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics());
    }
}
