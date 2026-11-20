package com.hsqs.pydroidcn;

import android.util.Log;

public final class Logx {
    public static final String TAG = "PydroidCN";
    private static final boolean ON = true;

    private Logx() {}

    public static void i(String m) {
        if (ON) Log.i(TAG, m);
    }

    public static void w(String m) {
        if (ON) Log.w(TAG, m);
    }

    public static void e(String m, Throwable t) {
        if (ON) Log.e(TAG, m, t);
    }
}
