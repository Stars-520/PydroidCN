package com.hsqs.pydroidcn;

import android.content.Context;

import java.lang.ref.WeakReference;

public final class AppCtx {
    private static volatile WeakReference<Context> ref;

    private AppCtx() {}

    static void set(Context c) {
        ref = new WeakReference<Context>(c);
    }

    static Context get() {
        WeakReference<Context> r = ref;
        return r == null ? null : r.get();
    }
}