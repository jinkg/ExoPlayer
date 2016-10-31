package com.yalin.exoplayer.source;

import com.yalin.exoplayer.Timeline;
import com.yalin.exoplayer.upstream.Allocator;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface MediaSource {
    interface Listener {
        void onSourceInfoRefreshed(Timeline timeline, Object manifest);
    }

    void prepareSource(Listener listener);

    void maybeThrowSourceInfoRefreshError() throws IOException;

    MediaPeriod createPeriod(int index, Allocator allocator, long positionUs);

    void releasePeriod(MediaPeriod mediaPeriod);

    void releaseSource();
}
