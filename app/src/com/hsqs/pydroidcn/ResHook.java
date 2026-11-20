package com.hsqs.pydroidcn;

import android.content.Context;
import android.content.res.Resources;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 资源层汉化。核心思路：不信 App 自己传进来的 Resources 实例（可能是它 newTheme 出来的），
 * 而是用宿主自己的 Resources 反查资源名，查到了就直接 setResult 覆盖。
 *
 * 覆盖入口：
 *  Resources.getString(int)
 *  Resources.getString(int, Object[])
 *  Resources.getText(int)                -> CharSequence
 *  Resources.getText(int, CharSequence)
 *  Resources.getQuantityString(int, int)
 *  Resources.getQuantityText(int, int)
 */
final class ResHook {

    private static volatile Resources HOST_RES;

    private ResHook() {}

    static void install(Context ctx) {
        try {
            HOST_RES = ctx.getResources();
        } catch (Throwable t) {
            Logx.e("get ctx resources fail", t);
            return;
        }
        if (HOST_RES == null) {
            Logx.w("ctx.getResources() is null, abort res hook");
            return;
        }
        Logx.i("res hook pkg=" + HOST_RES.getResourcePackageName(0x7f120078));

        int n = 0;
        for (Method m : Resources.class.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            String name = m.getName();
            if (p.length == 0 || p[0] != int.class) continue;

            boolean hit =
                    ("getString".equals(name) && (p.length == 1 || (p.length == 2 && p[1] == Object[].class)))
                            || ("getText".equals(name) && (p.length == 1 || (p.length == 2 && p[1] == CharSequence.class)))
                            || ("getQuantityString".equals(name) && p.length >= 2 && p[1] == int.class)
                            || ("getQuantityText".equals(name) && p.length == 2 && p[1] == int.class);
            if (!hit) continue;

            try {
                XposedBridge.hookMethod(m, new ResCallHook(p.length >= 2 && p[1] == Object[].class));
                n++;
                Logx.i("hook " + name + "/" + p.length + " ok");
            } catch (Throwable t) {
                Logx.w("hook " + name + "/" + p.length + " fail: " + t);
            }
        }
        Logx.i("res hook installed count=" + n + " dict=" + Dict.nameSize());
    }

    /** 用宿主 Resources 反查资源名，稳定且不受 theme 影响 */
    private static String resName(int id) {
        Resources r = HOST_RES;
        if (r == null) return null;
        try {
            return r.getResourceName(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /** string/xxx 与 plurals/xxx 都能落到同一个 key 上 */
    private static String lookup(int id) {
        String full = resName(id);
        if (full == null) return null;
        String zh = Dict.byResName(full);
        if (zh != null) return zh;
        int slash = full.lastIndexOf('/');
        if (slash > 0) zh = Dict.byResName(full.substring(slash + 1));
        return zh;
    }

    private static final class ResCallHook extends XC_MethodHook {
        private final boolean format;

        ResCallHook(boolean format) {
            this.format = format;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object res = param.getResult();
            // 原值为 null 表示资源缺失，保持原语义不动
            if (res == null) return;

            int id;
            try {
                id = (Integer) param.args[0];
            } catch (Throwable t) {
                return;
            }

            String zh = lookup(id);
            if (zh == null) return;

            if (format) {
                Object[] args;
                try {
                    args = (Object[]) param.args[1];
                } catch (Throwable t) {
                    args = null;
                }
                if (args != null && args.length > 0) {
                    try {
                        param.setResult(String.format(zh, args));
                        return;
                    } catch (Throwable ignore) {
                        // 格式串不匹配时退化成裸中文
                    }
                }
            }
            param.setResult(zh);
        }
    }
}