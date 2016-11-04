package com.yalin.exoplayer.util;

import android.os.SystemClock;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class StandaloneMediaClock implements MediaClock {

    private boolean started;

    /**
     * The media time when the clock was last set or stopped.
     */
    private long positionUs;

    /**
     * The difference between {@link SystemClock#elapsedRealtime()} and {@link #positionUs}
     * when the clock was last set or started.
     */
    private long deltaUs;

    /**
     * Starts the clock. Does nothing if the clock is already started.
     */
    public void start() {
        if (!started) {
            started = true;
            deltaUs = elapsedRealtimeMinus(positionUs);
        }
    }

    /**
     * Stops the clock. Does nothing if the clock is already stopped.
     */
    public void stop() {
        if (started) {
            positionUs = elapsedRealtimeMinus(deltaUs);
            started = false;
        }
    }

    /**
     * @param timeUs The position to set in microseconds.
     */
    public void setPositionUs(long timeUs) {
        this.positionUs = timeUs;
        deltaUs = elapsedRealtimeMinus(timeUs);
    }

    @Override
    public long getPositionUs() {
        return started ? elapsedRealtimeMinus(deltaUs) : positionUs;
    }

    private long elapsedRealtimeMinus(long toSubtractUs) {
        return SystemClock.elapsedRealtime() * 1000 - toSubtractUs;
    }
}
