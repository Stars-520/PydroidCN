package com.hsqs.pydroidcn;

import android.widget.TextView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 文本层汉化。App 里有一大批英文是直接写死在 Java 常量或 setText("Run") 里的，
 * 这些压根不走 resources，只能在 TextView 落字那一刻改。
 *
 * 三条路径同时挂：
 *  1. TextView.setText(CharSequence)      —— 最终都会汇到这里
 *  2. TextView.setText(int, BufferType)    —— 不走 1，单独挂
 *  3. TextView.setText(char[], int, int)   —— 编辑器/终端走这条，单独挂
 *
 * 只改「命中字典」的字符串，其余原样放行，零副作用。
 */
final class TextHook {

    private TextHook() {}

    static void install(ClassLoader cl) {
        Class<?> tv;
        try {
            tv = cl.loadClass("android.widget.TextView");
        } catch (Throwable t) {
            Logx.e("load TextView fail", t);
            return;
        }

        hookSetText(tv);
        Logx.i("text hook installed, text dict=" + Dict.textSize());
    }

    private static void hookSetText(Class<?> tv) {
        for (Method m : tv.getDeclaredMethods()) {
            String n = m.getName();
            if (!"setText".equals(n)) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 0) continue;

            boolean target =
                    (p.length == 1 && p[0] == CharSequence.class)
                            || (p.length == 2 && p[0] == CharSequence.class
                            && p[1].getName().equals("android.widget.TextView$BufferType"))
                            || (p.length == 1 && p[0] == int.class)
                            || (p.length == 3 && p[0] == char[].class && p[1] == int.class && p[2] == int.class);
            if (!target) continue;

            try {
                XposedBridge.hookMethod(m, new SetTextHook());
                Logx.i("hook TextView.setText/" + p.length + " ok");
            } catch (Throwable t) {
                Logx.w("hook setText/" + p.length + " fail: " + t);
            }
        }
    }

    private static final class SetTextHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            Class<?>[] p = ((Method) param.method).getParameterTypes();
            try {
                if (p.length == 1 && p[0] == CharSequence.class) {
                    CharSequence cs = (CharSequence) param.args[0];
                    String zh = translate(cs);
                    if (zh != null) param.args[0] = zh;

                } else if (p.length == 2 && p[0] == CharSequence.class) {
                    CharSequence cs = (CharSequence) param.args[0];
                    String zh = translate(cs);
                    if (zh != null) param.args[0] = zh;

                } else if (p.length == 1 && p[0] == int.class) {
                    // setText(int resid) 由 ResHook 覆盖，这里只管资源名查不到的情况
                    // 什么都不做，避免和 ResHook 双重改动
                    return;

                } else if (p.length == 3 && p[0] == char[].class) {
                    char[] buf = (char[]) param.args[0];
                    int start = (Integer) param.args[1];
                    int len = (Integer) param.args[2];
                    if (buf == null || len <= 0) return;
                    String raw = new String(buf, start, len);
                    String zh = Dict.applyText(raw);
                    if (zh.equals(raw)) return;
                    // 原数组装不下中文就换一个新数组塞回去
                    char[] nin = zh.toCharArray();
                    param.args[0] = nin;
                    param.args[1] = 0;
                    param.args[2] = nin.length;
                }
            } catch (Throwable t) {
                // 任何异常都放行原始调用，不能把界面搞崩
            }
        }

        private String translate(CharSequence cs) {
            if (cs == null) return null;
            String s = cs.toString();
            if (s.length() < 2) return null;
            // 已是中文就不动
            if (containsCJK(s)) return null;

            String exact = Dict.byText(s);
            if (exact != null) return exact;

            String rep = Dict.applyText(s);
            return rep.equals(s) ? null : rep;
        }

        private boolean containsCJK(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= 0x4E00 && c <= 0x9FFF) return true;
            }
            return false;
        }
    }
}