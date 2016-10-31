package com.yalin.exoplayer.source;

import com.yalin.exoplayer.upstream.Allocator;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class ConcatenatingMediaSource implements MediaSource {
    public ConcatenatingMediaSource(MediaSource... mediaSources) {
    }

    @Override
    public void prepareSource(Listener listener) {

    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {

    }

    @Override
    public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
        return null;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {

    }

    @Override
    public void releaseSource() {

    }
}
