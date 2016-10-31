package com.yalin.exoplayer;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class Timeline {
    public abstract int getWindowCount();

    public final Window getWindow(int windowIndex, Window window) {
        return getWindow(windowIndex, window, false);
    }

    public abstract Window getWindow(int windowIndex, Window window, boolean setIds);

    public abstract int getPeriodCount();

    public final Period getPeriod(int periodIndex, Period period) {
        return getPeriod(periodIndex, period, false);
    }

    public abstract Period getPeriod(int periodIndex, Period period, boolean setIds);

    public abstract int getIndexOfPeriod(Object uid);

    public static final class Window {
        public Object id;

        public long presentationStartTimeMs;

        public long windowStartTimeMs;

        public boolean isSeekable;

        public boolean isDynamic;

        public int firstPeriodIndex;

        public int lastPeriodIndex;

        private long defaultPositionUs;
        private long durationUs;
        private long positionInFirstPeriodUs;

        public Window set(Object id, long presentationStartTimeMs, long windowStartTimeMs,
                          boolean isSeekable, boolean isDynamic, long defaultPositionUs, long durationUs,
                          int firstPeriodIndex, int lastPeriodIndex, long positionInFirstPeriodUs) {
            this.id = id;
            this.presentationStartTimeMs = presentationStartTimeMs;
            this.windowStartTimeMs = windowStartTimeMs;
            this.isSeekable = isSeekable;
            this.isDynamic = isDynamic;
            this.defaultPositionUs = defaultPositionUs;
            this.durationUs = durationUs;
            this.firstPeriodIndex = firstPeriodIndex;
            this.lastPeriodIndex = lastPeriodIndex;
            this.positionInFirstPeriodUs = positionInFirstPeriodUs;
            return this;
        }

        public long getDefaultPositionMs() {
            return C.usToMs(defaultPositionUs);
        }

        public long getDefaultPositionUs() {
            return defaultPositionUs;
        }

        public long getDurationMs() {
            return C.usToMs(durationUs);
        }

        public long getDurationUs() {
            return durationUs;
        }

        public long getPositionInFirstPeriodMs() {
            return C.usToMs(positionInFirstPeriodUs);
        }

        public long getPositionInFirstPeriodUs() {
            return positionInFirstPeriodUs;
        }

    }

    public static final class Period {
        public Object id;

        public Object uid;

        public int windowIndex;

        private long durationUs;
        private long positionInWindowUs;

        public Period set(Object id, Object uid, int windowIndex, long durationUs,
                          long positionInWindowUs) {
            this.id = id;
            this.uid = uid;
            this.windowIndex = windowIndex;
            this.durationUs = durationUs;
            this.positionInWindowUs = positionInWindowUs;
            return this;
        }

        public long getDurationMs() {
            return C.usToMs(durationUs);
        }

        public long getDurationUs() {
            return durationUs;
        }

        public long getPositionInWindowMs() {
            return C.usToMs(positionInWindowUs);
        }

        public long getPositionInWindowUs() {
            return positionInWindowUs;
        }
    }
}
