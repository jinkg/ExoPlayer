package com.yalin.exoplayer.upstream;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public interface BandWidthMeter {
    interface EventListener {
        void onBandwidthSample(int elapsedMs, long bytes, long bitrate);
    }

    long NO_ESTIMATE = -1;

    long getBitrateEstimate();
}
