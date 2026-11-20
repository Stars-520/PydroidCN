package com.hsqs.pydroidcn;

import android.content.res.AssetManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * assets 层。Pydroid 的示例代码、aconsent 表单是 assets 里的静态文本，
 * 这里只处理纯文本类（.txt / .json / .html），二进制（tar.xz / busybox / ttf）一律不碰。
 *
 * 注意：finalpackage3_arm64.tar.xz 是 56MB 的 Python 运行环境，绝对不读进内存。
 */
final class AssetsHook {

    private static final String[] TEXT_EXT = {".txt", ".json", ".html", ".kv", ".cfg", ".ini"};

    private AssetsHook() {}

    static void install(ClassLoader cl) {
        try {
            hookOpen();
            Logx.i("assets hook installed");
        } catch (Throwable t) {
            Logx.w("assets hook fail: " + t);
        }
    }

    private static void hookOpen() {
        try {
            XposedHelpers.findAndHookMethod(AssetManager.class, "open", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patch(param);
                        }
                    });
            Logx.i("hook AssetManager.open(String) ok");
        } catch (Throwable t) {
            Logx.w("hook open(String) fail: " + t);
        }
    }

    private static void patch(XC_MethodHook.MethodHookParam param) {
        String path = (String) param.args[0];
        if (!isText(path)) return;

        InputStream raw = (InputStream) param.getResult();
        if (raw == null) return;

        String text = readAll(raw);
        if (text == null) return;

        String out = Dict.applyText(text);
        if (out.equals(text)) return;

        try {
            Logx.i("patch asset " + path + " " + text.length() + "->" + out.length());
            param.setResult(new ByteArrayInputStream(out.getBytes("UTF-8")));
        } catch (Throwable ignore) {
        }
    }

    private static boolean isText(String path) {
        if (path == null) return false;
        String p = path.toLowerCase();
        for (String e : TEXT_EXT) {
            if (p.endsWith(e)) return true;
        }
        return false;
    }

    private static String readAll(InputStream in) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                // 超过 512KB 的当二进制，直接弃用
                if (total > 512 * 1024) return null;
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            try { in.close(); } catch (Throwable ignore) {}
        }
    }
}