package com.yalin.exoplayer.source;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Timeline;
import com.yalin.exoplayer.util.Assertions;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class SinglePeriodTimeline extends Timeline {

    private static final Object ID = new Object();

    private final long periodDurationUs;
    private final long windowDurationUs;
    private final long windowPositionInPeriodUs;
    private final long windowDefaultStartPositionUs;
    private final boolean isSeekable;
    private final boolean isDynamic;

    public SinglePeriodTimeline(long durationUs, boolean isSeekable) {
        this(durationUs, durationUs, 0, 0, isSeekable, false);
    }

    public SinglePeriodTimeline(long periodDurationUs, long windowDurationUs,
                                long windowPositionInPeriodUs, long windowDefaultStartPositionUs, boolean isSeekable,
                                boolean isDynamic) {
        this.periodDurationUs = periodDurationUs;
        this.windowDurationUs = windowDurationUs;
        this.windowPositionInPeriodUs = windowPositionInPeriodUs;
        this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
        this.isSeekable = isSeekable;
        this.isDynamic = isDynamic;
    }

    @Override
    public int getWindowCount() {
        return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds) {
        Assertions.checkIndex(windowIndex, 0, 1);
        Object id = setIds ? ID : null;
        return window.set(id, C.TIME_UNSET, C.TIME_UNSET, isSeekable, isDynamic,
                windowDefaultStartPositionUs, windowDurationUs, 0, 0, windowPositionInPeriodUs);
    }

    @Override
    public int getPeriodCount() {
        return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
        Assertions.checkIndex(periodIndex, 0, 1);
        Object id = setIds ? ID : null;
        return period.set(id, id, 0, periodDurationUs, -windowPositionInPeriodUs);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
        return ID.equals(uid) ? 0 : C.INDEX_UNSET;
    }
}
