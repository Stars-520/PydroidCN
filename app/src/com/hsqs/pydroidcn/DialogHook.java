package com.hsqs.pydroidcn;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

/**
 * 对话框与 Toast。这两个走的是自己那套管线，不经过 TextView.setText 的常规路径
 * （AlertDialog 的 message 直接进 AlertController 的 TextView，Toast 在部分 ROM 上
 * 用 RemoteViews 跨进程渲染），单独补两条。
 */
final class DialogHook {

    private DialogHook() {}

    static void install(ClassLoader cl) {
        hookBuilderSetMessage();
        hookBuilderSetTitle();
        hookToastMakeText();
    }

    private static void hookBuilderSetMessage() {
        try {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                    AlertDialog.Builder.class, "setMessage",
                    CharSequence.class, new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            CharSequence cs = (CharSequence) param.args[0];
                            String zh = tr(cs);
                            if (zh != null) param.args[0] = zh;
                        }
                    });
            Logx.i("hook AlertDialog.Builder.setMessage ok");
        } catch (Throwable t) {
            Logx.w("hook setMessage fail: " + t);
        }
    }

    private static void hookBuilderSetTitle() {
        try {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                    AlertDialog.Builder.class, "setTitle",
                    CharSequence.class, new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            CharSequence cs = (CharSequence) param.args[0];
                            String zh = tr(cs);
                            if (zh != null) param.args[0] = zh;
                        }
                    });
            Logx.i("hook AlertDialog.Builder.setTitle ok");
        } catch (Throwable t) {
            Logx.w("hook setTitle fail: " + t);
        }
    }

    private static void hookToastMakeText() {
        // 两参版在 API 30 起走 「(Context, CharSequence) 内部实现」，
        // 这里把三参版的两个重载都挂上，覆盖老路径
        try {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                    Toast.class, "makeText",
                    android.content.Context.class, CharSequence.class, int.class,
                    new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            CharSequence cs = (CharSequence) param.args[1];
                            String zh = tr(cs);
                            if (zh != null) param.args[1] = zh;
                        }
                    });
            Logx.i("hook Toast.makeText ok");
        } catch (Throwable t) {
            Logx.w("hook Toast.makeText fail: " + t);
        }
    }

    private static String tr(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString();
        if (s.length() < 2) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return null;
        }
        String exact = Dict.byText(s);
        if (exact != null) return exact;
        String rep = Dict.applyText(s);
        return rep.equals(s) ? null : rep;
    }
}