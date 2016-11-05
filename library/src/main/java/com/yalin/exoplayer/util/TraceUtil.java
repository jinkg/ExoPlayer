package com.yalin.exoplayer.util;

import android.annotation.TargetApi;

import com.yalin.exoplayer.ExoPlayerLibraryInfo;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public final class TraceUtil {
    private TraceUtil() {
    }

    public static void beginSection(String sectionName) {
        if (ExoPlayerLibraryInfo.TRACE_ENABLED && Util.SDK_INT >= 18) {
            beginSectionV18(sectionName);
        }
    }

    /**
     * Writes a trace message to indicate that a given section of code has ended.
     *
     * @see android.os.Trace#endSection()
     */
    public static void endSection() {
        if (ExoPlayerLibraryInfo.TRACE_ENABLED && Util.SDK_INT >= 18) {
            endSectionV18();
        }
    }

    @TargetApi(18)
    private static void beginSectionV18(String sectionName) {
        android.os.Trace.beginSection(sectionName);
    }

    @TargetApi(18)
    private static void endSectionV18() {
        android.os.Trace.endSection();
    }
}
