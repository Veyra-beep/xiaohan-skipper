package com.xiaohan.skipper;

import java.util.ArrayList;
import java.util.List;

/** 内置规则表。按优先级从上到下匹配，命中即停。 */
public final class Rules {

    private static final List<Rule> LIST = new ArrayList<>();

    static {
        // 强特征：文字里直接带「跳过」/Skip，放宽位置限制
        LIST.add(new Rule("文字-跳过", "跳\\s*过|略\\s*过|skip|Skip|SKIP", null, null, false));
        // 关闭类
        LIST.add(new Rule("文字-关闭", "关闭广告|关\\s*闭|close ad|Close", null, null, true));
        // 无障碍描述
        LIST.add(new Rule("描述-跳过", null, "跳\\s*过|skip|关闭广告|关闭", null, true));
        // 资源 id 常见命名（穿山甲 tt_splash_skip_btn / 优量汇 / 快手 ksad_splash_skip 等都在右上角）
        LIST.add(new Rule("id-跳过", null, null,
                "(?i).*(skip|skip_?btn|skip_?view|skip_?ad|ad_?skip|btn_?skip|close_?ad|ad_?close|tt_?skip|ksad_?skip|gdt_?skip|splash_?skip).*",
                true));
        // 兜底：右上角的纯数字倒计时（很多 SDK 倒计时和跳过合一）
        LIST.add(new Rule("倒计时角标", "^\\s*\\d{1,2}\\s*[sS秒]?\\s*$", null, null, true));
    }

    private Rules() { }

    public static List<Rule> all() { return LIST; }
}
