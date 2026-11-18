package com.xiaohan.skipper;

import android.graphics.Rect;

import java.util.regex.Pattern;

/** 单条跳过规则 */
public final class Rule {

    public final String name;
    /** 匹配 text */
    public final Pattern text;
    /** 匹配 contentDescription */
    public final Pattern desc;
    /** 匹配 viewIdResourceName，如 com.foo:id/skip */
    public final Pattern id;
    /** 是否要求落在屏幕右上区域 */
    public final boolean needCorner;

    public Rule(String name, String textRe, String descRe, String idRe, boolean needCorner) {
        this.name = name;
        this.text = textRe == null ? null : Pattern.compile(textRe);
        this.desc = descRe == null ? null : Pattern.compile(descRe);
        this.id = idRe == null ? null : Pattern.compile(idRe);
        this.needCorner = needCorner;
    }

    public boolean matchesText(CharSequence t) {
        return text != null && t != null && text.matcher(t).find();
    }

    public boolean matchesDesc(CharSequence d) {
        return desc != null && d != null && desc.matcher(d).find();
    }

    public boolean matchesId(String i) {
        return id != null && i != null && id.matcher(i).find();
    }

    /** 只按纯数字倒计时算：3s / 3秒 / 5 */
    public static boolean looksLikeCountdown(CharSequence t) {
        if (t == null) return false;
        String s = t.toString().trim();
        if (s.isEmpty() || s.length() > 4) return false;
        return s.matches("^\\d{1,2}\\s*[sS秒]?$");
    }

    /** 右上角判定：屏幕宽度 45% 之后，高度 30% 之前 */
    public static boolean inCorner(Rect r, int screenW, int screenH) {
        if (r == null || screenW <= 0 || screenH <= 0) return false;
        int cx = (r.left + r.right) / 2;
        int cy = (r.top + r.bottom) / 2;
        return cx > screenW * 0.45f && cy < screenH * 0.30f;
    }
}
