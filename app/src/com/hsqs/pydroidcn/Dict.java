package com.hsqs.pydroidcn;

import java.util.HashMap;
import java.util.Map;

/**
 * 字典门面。数据由 DictData 自动生成注入。
 * NAME: 资源名（string/xxx 或纯名 xxx）-> 中文
 * TEXT: 英文界面字面量 -> 中文（长串优先）
 */
public final class Dict {

    private static final Map<String, String> NAME = new HashMap<>();
    private static final Map<String, String> TEXT = new HashMap<>();
    private static String[] TEXT_KEYS_SORTED = new String[0];
    private static boolean inited = false;

    private Dict() {}

    public static synchronized void init() {
        if (inited) return;
        inited = true;
        DictData.fillName(NAME);
        DictData.fillText(TEXT);
        // 长 key 优先，避免 "Open" 抢先吃掉 "Open project"
        String[] keys = TEXT.keySet().toArray(new String[0]);
        java.util.Arrays.sort(keys, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        TEXT_KEYS_SORTED = keys;
        Logx.i("dict loaded name=" + NAME.size() + " text=" + TEXT.size());
    }

    public static int nameSize() { return NAME.size(); }
    public static int textSize() { return TEXT.size(); }

    /** 资源名查询，支持 "pkg:string/xxx"、"string/xxx"、"xxx" 三种写法 */
    public static String byResName(String fullName) {
        if (fullName == null) return null;
        String v = NAME.get(fullName);
        if (v != null) return v;
        int c = fullName.indexOf(':');
        String tail = c > 0 ? fullName.substring(c + 1) : fullName;
        v = NAME.get(tail);
        if (v != null) return v;
        int s = tail.indexOf('/');
        if (s >= 0) {
            v = NAME.get(tail.substring(s + 1));
            if (v != null) return v;
        }
        return null;
    }

    /** 英文原文精确查中文 */
    public static String byText(String src) {
        return src == null ? null : TEXT.get(src);
    }

    /** 整段文本替换，长串优先，只做词边界较安全的全量替换 */
    public static String applyText(String src) {
        if (src == null || src.length() == 0 || TEXT.isEmpty()) return src;
        String out = src;
        for (String k : TEXT_KEYS_SORTED) {
            if (out.contains(k)) {
                out = out.replace(k, TEXT.get(k));
            }
        }
        return out;
    }

    /** 供 ResHook 使用的可变表，避免每次反射拿 key */
    static Map<String, String> nameMap() { return NAME; }
}
