package com.xiaohan.skipper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 核心服务：前台窗口切换 → 在保护窗口内轮询 → 命中规则 → 点击「跳过」。
 *
 * 防误触硬约束：
 *  1. 只在应用刚切到前台后的 windowMs 内工作，进入正文后彻底静默；
 *  2. 一次前台切换最多点一次；
 *  3. 宽松规则（描述 / id / 倒计时）必须落在屏幕右上角；
 *  4. 跳过自己、系统界面、输入法。
 */
public class SkipService extends AccessibilityService {

    /** 刚切前台时密集扫描的间隔 */
    private static final int FAST_MS = 120;
    /** 保护窗口后半段放慢，省电 */
    private static final int SLOW_MS = 320;
    private static final long FAST_FOR_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Prefs prefs;
    private int screenW = 1080;
    private int screenH = 1920;

    private String fgPkg;
    private long fgSince;
    private int clickedThisRound;
    private int nextDelay = FAST_MS;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            boolean again = false;
            try {
                again = tick();
            } catch (Throwable t) {
                log("扫描异常: " + t);
            }
            if (again) handler.postDelayed(this, nextDelay);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
        SkipLog.init(this);
        SkipLog.loadFromDisk(this);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        log("服务已连接  屏幕 " + screenW + "x" + screenH
                + "  保护窗口 " + prefs.windowMs() + "ms"
                + "  延迟 " + prefs.delayMs() + "ms");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || prefs == null || !prefs.enabled()) return;

        int type = event.getEventType();
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (pkg.isEmpty() || ignored(pkg)) return;

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !pkg.equals(fgPkg)) {
            fgPkg = pkg;
            fgSince = SystemClock.elapsedRealtime();
            clickedThisRound = 0;
            nextDelay = FAST_MS;
            handler.removeCallbacks(pollTask);
            if (pkgAllowed(pkg)) handler.post(pollTask);
        }
    }

    private boolean ignored(String pkg) {
        if (pkg.equals(getPackageName())) return true;
        if (pkg.equals("com.android.systemui")) return true;
        if (pkg.startsWith("com.android.inputmethod")) return true;
        if (pkg.equals("android")) return true;
        return false;
    }

    /** @return 是否继续轮询 */
    private boolean tick() {
        if (prefs == null || !prefs.enabled()) return false;
        if (fgPkg == null) return false;
        if (clickedThisRound >= 1) return false;

        long age = SystemClock.elapsedRealtime() - fgSince;
        int window = prefs.windowMs();
        if (age > window) return false;
        if (!pkgAllowed(fgPkg)) return false;
        nextDelay = age < FAST_FOR_MS ? FAST_MS : SLOW_MS;

        List<AccessibilityNodeInfo> nodes = gather();
        if (nodes.isEmpty()) return true;

        for (Rule rule : Rules.all()) {
            for (AccessibilityNodeInfo n : nodes) {
                if (!isUsable(n)) continue;
                if (!ruleMatches(rule, n)) continue;
                Rect r = new Rect();
                try {
                    n.getBoundsInScreen(r);
                } catch (Throwable t) {
                    continue;
                }
                if (rule.needCorner && !Rule.inCorner(r, screenW, screenH)) continue;

                final String label = safeText(n);
                int delay = prefs.delayMs();
                if (delay > 0) {
                    clickedThisRound++;
                    final AccessibilityNodeInfo fn = n;
                    final Rect fr = new Rect(r);
                    final String fRule = rule.name;
                    final long fAge = age;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            boolean ok = clickSafe(fn, fr);
                            log((ok ? "命中" : "点击失败") + "[" + fRule + "] " + fgPkg
                                    + " \"" + label + "\" " + fr.width() + "x" + fr.height()
                                    + "@(" + fr.centerX() + "," + fr.centerY() + ") 用时" + fAge + "ms");
                            if (ok && prefs.vibrate()) vibrate();
                        }
                    }, delay);
                    return false;
                }

                if (clickSafe(n, r)) {
                    clickedThisRound++;
                    log(String.format(Locale.US, "命中[%s] %s \"%s\" %dx%d@(%d,%d) 用时%dms",
                            rule.name, fgPkg, label, r.width(), r.height(),
                            r.centerX(), r.centerY(), age));
                    if (prefs.vibrate()) vibrate();
                    return false;
                }
            }
        }
        return true;
    }

    private boolean pkgAllowed(String pkg) {
        return prefs.pkgMode() == 0 || prefs.isListed(pkg);
    }

    private void log(String s) {
        if (prefs != null && prefs.logEnabled()) SkipLog.add(s);
    }

    /** 收集当前活动窗口 + 所有可见窗口的节点 */
    private List<AccessibilityNodeInfo> gather() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        AccessibilityNodeInfo root = null;
        try { root = getRootInActiveWindow(); } catch (Throwable ignored) { }
        if (root != null) collect(root, out, 0);

        if (out.size() < 8) {
            try {
                for (AccessibilityWindowInfo w : getWindows()) {
                    if (w == null) continue;
                    if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null) collect(r, out, 0);
                }
            } catch (Throwable ignored) { }
        }
        return out;
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int depth) {
        if (node == null || depth > 30 || out.size() > 500) return;
        out.add(node);
        int n;
        try {
            n = node.getChildCount();
        } catch (Throwable t) {
            return;
        }
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo c = null;
            try { c = node.getChild(i); } catch (Throwable ignored) { }
            if (c != null) collect(c, out, depth + 1);
        }
    }

    private boolean isUsable(AccessibilityNodeInfo n) {
        try {
            if (!n.isVisibleToUser()) return false;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.width() < 12 || r.height() < 12) return false;
            // 必须在屏幕内
            return r.centerX() >= 0 && r.centerX() <= screenW
                    && r.centerY() >= 0 && r.centerY() <= screenH;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean ruleMatches(Rule rule, AccessibilityNodeInfo n) {
        try {
            if (rule.matchesText(n.getText())) return true;
            if (rule.matchesDesc(n.getContentDescription())) return true;
            if (Build.VERSION.SDK_INT >= 18 && rule.matchesId(n.getViewIdResourceName())) return true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private String safeText(AccessibilityNodeInfo n) {
        try {
            CharSequence t = n.getText();
            if (t == null) t = n.getContentDescription();
            if (t == null) t = n.getViewIdResourceName();
            if (t == null) return "";
            String s = t.toString().replace('\n', ' ');
            return s.length() > 24 ? s.substring(0, 24) : s;
        } catch (Throwable t) {
            return "";
        }
    }

    private boolean clickSafe(AccessibilityNodeInfo node, Rect r) {
        try {
            return doClick(node, r);
        } catch (Throwable t) {
            return gestureTap(r.centerX(), r.centerY());
        }
    }

    /** 无障碍点击优先，坐标手势兜底 */
    private boolean doClick(AccessibilityNodeInfo node, Rect r) {
        AccessibilityNodeInfo target = node;
        int guard = 0;
        while (target != null && guard++ < 6) {
            boolean clickable;
            try {
                clickable = target.isClickable();
            } catch (Throwable t) {
                clickable = false;
            }
            if (clickable) {
                try {
                    if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                } catch (Throwable ignored) { }
                break;
            }
            try { target = target.getParent(); } catch (Throwable t) { target = null; }
        }
        return gestureTap(r.centerX(), r.centerY());
    }

    private boolean gestureTap(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) return false;
        try {
            Path p = new Path();
            p.moveTo(Math.max(1, x), Math.max(1, y));
            GestureDescription gd = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 0, 40))
                    .build();
            return dispatchGesture(gd, null, null);
        } catch (Throwable t) {
            log("手势失败: " + t);
            return false;
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(30);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onInterrupt() {
        log("服务被打断");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        log("服务已断开");
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }
}