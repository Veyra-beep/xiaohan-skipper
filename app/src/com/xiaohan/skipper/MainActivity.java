package com.xiaohan.skipper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private Prefs prefs;
    private TextView statusView;
    private TextView logView;
    private Button masterBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        SkipLog.init(this);
        SkipLog.loadFromDisk(this);
        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F6F7F9"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题
        TextView title = new TextView(this);
        title.setText("小霜跳过");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#111827"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("开屏广告自动跳过 · 纯本地 · 无网络权限");
        sub.setTextSize(12);
        sub.setTextColor(Color.parseColor("#6B7280"));
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        // 状态卡
        LinearLayout card = card();
        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor(Color.parseColor("#374151"));
        statusView.setLineSpacing(dp(3), 1f);
        card.addView(statusView);

        masterBtn = new Button(this);
        masterBtn.setAllCaps(false);
        masterBtn.setTextSize(15);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        bp.topMargin = dp(12);
        card.addView(masterBtn, bp);
        root.addView(card);

        // 开关区
        LinearLayout sw = card();
        sw.addView(sectionTitle("行为"));

        CheckBox cbEnable = check("总开关（关闭后完全不工作）", prefs.enabled());
        cbEnable.setOnCheckedChangeListener((b, v) -> { prefs.setEnabled(v); refresh(); });
        sw.addView(cbEnable);

        CheckBox cbVib = check("跳过成功时轻微震动", prefs.vibrate());
        cbVib.setOnCheckedChangeListener((b, v) -> prefs.setVibrate(v));
        sw.addView(cbVib);

        CheckBox cbLog = check("记录跳过日志", prefs.logEnabled());
        cbLog.setOnCheckedChangeListener((b, v) -> prefs.setLogEnabled(v));
        sw.addView(cbLog);

        sw.addView(label("开屏保护窗口：只在应用启动后这段时间内点击，超时视为已进正文"));
        sw.addView(slider(prefs.windowMs() / 1000, 3, 30, "秒", v -> prefs.setWindowMs(v * 1000)));

        sw.addView(label("点击延迟：发现按钮后等一会儿再点（部分 SDK 需要）"));
        sw.addView(slider(prefs.delayMs() / 100, 0, 20, "×100ms", v -> prefs.setDelayMs(v * 100)));
        root.addView(sw);

        // 应用范围
        LinearLayout sc = card();
        sc.addView(sectionTitle("生效范围"));
        TextView modeTv = new TextView(this);
        modeTv.setTextSize(13);
        modeTv.setTextColor(Color.parseColor("#374151"));
        modeTv.setText(prefs.pkgMode() == 0 ? "当前：全部应用" : "当前：仅白名单（" + prefs.pkgs().size() + " 个）");
        sc.addView(modeTv);

        Button editList = new Button(this);
        editList.setAllCaps(false);
        editList.setText("编辑白名单 / 切换范围");
        editList.setOnClickListener(v -> showPkgDialog());
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        ep.topMargin = dp(10);
        sc.addView(editList, ep);
        root.addView(sc);

        // 系统设置快捷入口
        LinearLayout sys = card();
        sys.addView(sectionTitle("保持存活（必做）"));
        sys.addView(label("系统可能杀掉无障碍服务，建议把下面三项都设上。"));
        sys.addView(actionBtn("① 无障碍设置（开启本服务）", v -> openAccessibility()));
        sys.addView(actionBtn("② 电池优化白名单", v -> requestIgnoreBattery()));
        sys.addView(actionBtn("③ 本应用详情页（自启动/后台运行）", v -> openAppDetail()));
        sys.addView(actionBtn("④ 锁住最近任务（防清理）", v -> toast("在最近任务里长按本应用卡片 → 锁定")));
        root.addView(sys);

        // 日志
        LinearLayout lc = card();
        lc.addView(sectionTitle("运行日志"));
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.parseColor("#4B5563"));
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setLineSpacing(dp(2), 1f);
        lc.addView(logView);

        Button refreshLog = new Button(this);
        refreshLog.setAllCaps(false);
        refreshLog.setText("刷新 / 清空");
        refreshLog.setOnClickListener(v -> { SkipLog.clear(); refresh(); });
        lc.addView(refreshLog);
        root.addView(lc);

        TextView foot = new TextView(this);
        foot.setText("规则全部内置在本地代码里，不申请 INTERNET 权限，任何数据都不出设备。\n"
                + "若某个应用跳不掉，把该应用加进白名单再试，或看日志里是否有命中记录。");
        foot.setTextSize(11);
        foot.setTextColor(Color.parseColor("#9CA3AF"));
        foot.setPadding(0, dp(12), 0, 0);
        root.addView(foot);

        return sv;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(Color.WHITE);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        l.setLayoutParams(p);
        return l;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(Color.parseColor("#111827"));
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#6B7280"));
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private CheckBox check(String s, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextSize(13);
        c.setChecked(on);
        c.setPadding(0, dp(2), 0, dp(2));
        return c;
    }

    private Button actionBtn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        p.topMargin = dp(6);
        b.setLayoutParams(p);
        return b;
    }

    private interface IntCb { void on(int v); }

    private View slider(int cur, int min, int max, String unit, IntCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView val = new TextView(this);
        val.setTextSize(12);
        val.setTextColor(Color.parseColor("#374151"));
        val.setText(cur + " " + unit);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, cur - min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + min;
                val.setText(v + " " + unit);
                cb.on(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(val);
        box.addView(sb);
        return box;
    }

    // ---------------------------------------------------------------- 状态

    private boolean accessibilityOn() {
        try {
            ComponentName me = new ComponentName(this, SkipService.class);
            String flat = me.flattenToString();
            String on = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (on == null) return false;
            String lower = on.toLowerCase(Locale.US);
            return lower.contains(flat.toLowerCase(Locale.US))
                    || lower.contains(getPackageName().toLowerCase(Locale.US) + "/");
        } catch (Throwable t) {
            return false;
        }
    }

    private void refresh() {
        boolean on = accessibilityOn();
        StringBuilder sb = new StringBuilder();
        sb.append(on ? "✅ 无障碍服务：已开启" : "❌ 无障碍服务：未开启");
        sb.append("\n").append(prefs.enabled() ? "✅ 总开关：开" : "⛔ 总开关：关");
        sb.append("\n").append("⏱ 保护窗口 ").append(prefs.windowMs() / 1000).append("s")
                .append("  延迟 ").append(prefs.delayMs()).append("ms");
        sb.append("\n").append("📦 范围：")
                .append(prefs.pkgMode() == 0 ? "全部应用" : "白名单 " + prefs.pkgs().size() + " 个");
        if (statusView != null) statusView.setText(sb.toString());

        if (masterBtn != null) {
            masterBtn.setText(on ? "查看无障碍设置" : "去开启无障碍服务");
            masterBtn.setOnClickListener(v -> openAccessibility());
        }

        if (logView != null) {
            List<String> all = SkipLog.all();
            if (all.isEmpty()) {
                logView.setText("（暂无记录，去打开一个有开屏广告的 App 试试）");
            } else {
                int from = Math.max(0, all.size() - 60);
                StringBuilder l = new StringBuilder();
                for (int i = all.size() - 1; i >= from; i--) {
                    l.append(all.get(i)).append("\n");
                }
                logView.setText(l.toString());
            }
        }
    }

    // ---------------------------------------------------------------- 白名单

    private void showPkgDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(10), pad, 0);

        CheckBox all = new CheckBox(this);
        all.setText("全部应用生效（推荐）");
        all.setTextSize(14);
        all.setChecked(prefs.pkgMode() == 0);
        box.addView(all);

        box.addView(label("不勾「全部应用」时，只处理下面勾选的应用。"));

        ScrollView sc = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView loading = new TextView(this);
        loading.setText("正在读取已安装应用…");
        loading.setTextSize(12);
        loading.setTextColor(Color.parseColor("#9CA3AF"));
        loading.setPadding(0, dp(10), 0, dp(10));
        list.addView(loading);

        box.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        final Set<String> chosen = new LinkedHashSet<>(prefs.pkgs());
        loadApps(list, loading, chosen);

        new AlertDialog.Builder(this)
                .setTitle("生效范围")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setPkgs(chosen);
                    prefs.setPkgMode(all.isChecked() ? 0 : 1);
                    toast("已保存：" + (all.isChecked() ? "全部应用" : chosen.size() + " 个应用"));
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 后台读取可启动的应用列表，回填成勾选框 */
    private void loadApps(final LinearLayout list, final TextView loading, final Set<String> chosen) {
        new Thread(() -> {
            final List<String[]> rows = new ArrayList<>();
            try {
                Intent main = new Intent(Intent.ACTION_MAIN);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager pm = getPackageManager();
                List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
                Set<String> seen = new LinkedHashSet<>();
                for (ResolveInfo ri : ris) {
                    if (ri == null || ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || pkg.equals(getPackageName())) continue;
                    if (!seen.add(pkg)) continue;
                    String label;
                    try {
                        label = String.valueOf(pm.getApplicationLabel(ri.activityInfo.applicationInfo));
                    } catch (Throwable t) {
                        label = pkg;
                    }
                    rows.add(new String[]{label, pkg});
                }
                Collections.sort(rows, new Comparator<String[]>() {
                    @Override
                    public int compare(String[] a, String[] b) {
                        return a[0].compareToIgnoreCase(b[0]);
                    }
                });
            } catch (Throwable ignored) { }

            runOnUiThread(() -> {
                if (isFinishing()) return;
                list.removeAllViews();
                if (rows.isEmpty()) {
                    loading.setText("没读到应用列表（系统限制），改用「全部应用」模式即可。");
                    list.addView(loading);
                    return;
                }
                for (String[] row : rows) {
                    final String pkg = row[1];
                    CheckBox c = new CheckBox(this);
                    c.setText(row[0] + "\n" + pkg);
                    c.setTextSize(13);
                    c.setChecked(chosen.contains(pkg));
                    c.setOnCheckedChangeListener((b, v) -> {
                        if (v) chosen.add(pkg);
                        else chosen.remove(pkg);
                    });
                    list.addView(c);
                }
            });
        }).start();
    }

    // ---------------------------------------------------------------- 跳转

    private void openAccessibility() {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            toast("在列表里找到「小霜跳过」并开启");
        } catch (Throwable t) {
            toast("请手动进入 设置 → 无障碍");
        }
    }

    private void requestIgnoreBattery() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            }
        } catch (Throwable ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable t) {
            toast("请手动进入 设置 → 电池 → 电池优化");
        }
    }

    private void openAppDetail() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            toast("请手动进入 设置 → 应用 → 小霜跳过");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
