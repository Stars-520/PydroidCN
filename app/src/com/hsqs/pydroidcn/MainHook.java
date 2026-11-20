package com.hsqs.pydroidcn;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 入口。Pydroid 3 = ru.iiec.pydroid3。
 * 冷启动时挂 Application.attach，之后安装资源钩子和文本钩子。
 */
public class MainHook implements IXposedHookLoadPackage {

    public static final String PKG = "ru.iiec.pydroid3";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;
        Logx.i("loaded into " + lpparam.processName);

        Dict.init();

        hookAttach(lpparam.classLoader);
        hookMakeApp(lpparam.classLoader);
    }

    /** 主路径：Application.attach(Context) 之后装钩子 */
    private void hookAttach(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach",
                    Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.args[0];
                    if (ctx == null) return;
                    Logx.i("attach pkg=" + ctx.getPackageName());
                    installAll(ctx, cl);
                }
            });
            Logx.i("hook Application.attach ok");
        } catch (Throwable t) {
            Logx.e("hook attach fail", t);
        }
    }

    /** 兜底：部分 ROM 上 attach 早于 Xposed 生效，用 Resources 首次创建再补一次 */
    private void hookMakeApp(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getText",
                    int.class, new XC_MethodHook() {
                private boolean done = false;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (done) return;
                    done = true;
                    Context ctx = AppCtx.get();
                    if (ctx != null) installAll(ctx, cl);
                }
            });
        } catch (Throwable t) {
            Logx.w("hook Resources.getText probe fail: " + t);
        }
    }

    private volatile boolean installed = false;

    private synchronized void installAll(Context ctx, ClassLoader cl) {
        if (installed) return;
        installed = true;
        AppCtx.set(ctx);
        try {
            ResHook.install(ctx);
        } catch (Throwable t) {
            Logx.e("ResHook install fail", t);
        }
        try {
            TextHook.install(cl);
        } catch (Throwable t) {
            Logx.e("TextHook install fail", t);
        }
        try {
            DialogHook.install(cl);
        } catch (Throwable t) {
            Logx.e("DialogHook install fail", t);
        }
        try {
            AssetsHook.install(cl);
        } catch (Throwable t) {
            Logx.e("AssetsHook install fail", t);
        }
    }
}