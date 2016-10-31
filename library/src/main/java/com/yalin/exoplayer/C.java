package com.yalin.exoplayer;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class C {
    private C() {
    }

    public static final long TIME_UNSET = Long.MIN_VALUE + 1;

    public static final int INDEX_UNSET = -1;

    public static final int TRACK_TYPE_DEFAULT = 0;

    public static final int TRACK_TYPE_AUDIO = 1;

    public static final int TRACK_TYPE_VIDEO = 2;

    public static final int TRACK_TYPE_TEXT = 3;

    public static final int TRACK_TYPE_METADATA = 4;

    public static final int MSG_SET_SURFACE = 1;

    public static final int MSG_SET_VOLUME = 2;

    public static final int MSG_SET_PLAYBACK_PARAMS = 3;

    public static long usToMs(long timeUs) {
        return timeUs == TIME_UNSET ? TIME_UNSET : (timeUs / 1000);
    }
}
