package com.xiaohan.skipper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

/** 设置存储 */
public final class Prefs {

    private static final String FILE = "skipper";

    public static final String K_ENABLED = "enabled";
    public static final String K_WINDOW_MS = "window_ms";
    public static final String K_DELAY_MS = "delay_ms";
    public static final String K_PKGS = "pkgs";
    public static final String K_PKG_MODE = "pkg_mode";      // 0 = 全部应用, 1 = 仅名单
    public static final String K_VIBRATE = "vibrate";
    public static final String K_LOG = "log";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean enabled() { return sp.getBoolean(K_ENABLED, true); }
    public void setEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    /** 开屏后多少毫秒内才允许动手，超时视为已进入正文，不再点击 */
    public int windowMs() { return sp.getInt(K_WINDOW_MS, 12000); }
    public void setWindowMs(int v) { sp.edit().putInt(K_WINDOW_MS, v).apply(); }

    /** 发现按钮后延迟多少毫秒再点（0 = 立即） */
    public int delayMs() { return sp.getInt(K_DELAY_MS, 0); }
    public void setDelayMs(int v) { sp.edit().putInt(K_DELAY_MS, v).apply(); }

    public int pkgMode() { return sp.getInt(K_PKG_MODE, 0); }
    public void setPkgMode(int v) { sp.edit().putInt(K_PKG_MODE, v).apply(); }

    public boolean vibrate() { return sp.getBoolean(K_VIBRATE, false); }
    public void setVibrate(boolean v) { sp.edit().putBoolean(K_VIBRATE, v).apply(); }

    public boolean logEnabled() { return sp.getBoolean(K_LOG, true); }
    public void setLogEnabled(boolean v) { sp.edit().putBoolean(K_LOG, v).apply(); }

    public Set<String> pkgs() {
        Set<String> s = sp.getStringSet(K_PKGS, null);
        return s == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(s);
    }

    public void setPkgs(Set<String> s) { sp.edit().putStringSet(K_PKGS, new LinkedHashSet<String>(s)).apply(); }

    public boolean isListed(String pkg) {
        if (pkg == null) return false;
        for (String p : pkgs()) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }
}
