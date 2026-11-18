package com.xiaohan.skipper;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 极简运行日志：内存环形缓冲 + 落盘，供设置页查看 */
public final class SkipLog {

    private static final int MAX_MEM = 300;
    private static final ArrayDeque<String> BUF = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static File file;

    public static synchronized void init(Context c) {
        if (file != null) return;
        file = new File(c.getApplicationContext().getFilesDir(), "skip.log");
    }

    public static synchronized void add(String line) {
        String s = FMT.format(new Date()) + "  " + line;
        BUF.addLast(s);
        while (BUF.size() > MAX_MEM) BUF.removeFirst();
        if (file != null) {
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write((s + "\n").getBytes("UTF-8"));
            } catch (Exception ignored) { }
        }
    }

    public static synchronized List<String> all() {
        return new ArrayList<>(BUF);
    }

    /** 服务进程被杀后重启时，从磁盘回填 */
    public static synchronized void loadFromDisk(Context c) {
        init(c);
        if (file == null || !file.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String ln;
            int n = 0;
            ArrayDeque<String> tmp = new ArrayDeque<>();
            while ((ln = r.readLine()) != null) {
                tmp.addLast(ln);
                if (++n > MAX_MEM) tmp.removeFirst();
            }
            BUF.clear();
            BUF.addAll(tmp);
        } catch (Exception ignored) { }
    }

    public static synchronized void clear() {
        BUF.clear();
        if (file != null && file.exists()) {
            try { new FileOutputStream(file).close(); } catch (Exception ignored) { }
        }
    }
}
