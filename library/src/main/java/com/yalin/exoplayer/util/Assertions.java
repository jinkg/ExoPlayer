package com.yalin.exoplayer.util;

import android.os.Looper;
import android.text.TextUtils;

/**
 * 作者：YaLin
 * 日期：2016/10/28.
 */

public final class Assertions {
    private static final boolean ASSERTIONS_ENABLE = true;

    private Assertions() {

    }

    public static void checkArgument(boolean expression) {
        if (ASSERTIONS_ENABLE && !expression) {
            throw new IllegalArgumentException();
        }
    }

    public static void checkArgument(boolean expression, Object errorMessage) {
        if (ASSERTIONS_ENABLE && !expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    public static int checkIndex(int index, int start, int limit) {
        if (index < start || index >= limit) {
            throw new IndexOutOfBoundsException();
        }
        return index;
    }

    public static void checkState(boolean expression) {
        if (ASSERTIONS_ENABLE && !expression) {
            throw new IllegalStateException();
        }
    }

    public static void checkState(boolean expression, Object errorMessage) {
        if (ASSERTIONS_ENABLE && !expression) {
            throw new IllegalStateException(String.valueOf(errorMessage));
        }
    }

    public static <T> T checkNotNull(T reference) {
        if (ASSERTIONS_ENABLE && reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    public static <T> T checkNotNull(T reference, Object errorMessage) {
        if (ASSERTIONS_ENABLE && reference == null) {
            throw new NullPointerException(String.valueOf(errorMessage));
        }
        return reference;
    }

    public static String checkNotEmpty(String string) {
        if (ASSERTIONS_ENABLE && TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException();
        }
        return string;
    }

    public static String checkNotEmpty(String string, Object errorMessage) {
        if (ASSERTIONS_ENABLE && TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
        return string;
    }

    public static void checkMainThread() {
        if (ASSERTIONS_ENABLE && Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Not in applications main thread");
        }
    }
}
