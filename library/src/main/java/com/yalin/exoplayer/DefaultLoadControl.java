package com.yalin.exoplayer;

import com.yalin.exoplayer.source.TrackGroupArray;
import com.yalin.exoplayer.trackslection.TrackSelections;
import com.yalin.exoplayer.upstream.Allocator;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class DefaultLoadControl implements LoadControl{
    @Override
    public void onPrepared() {

    }

    @Override
    public void onTrackSelected(Renderer[] renderers, TrackGroupArray trackGroups, TrackSelections<?> trackSelections) {

    }

    @Override
    public void onStopped() {

    }

    @Override
    public void onReleased() {

    }

    @Override
    public Allocator getAllocator() {
        return null;
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering) {
        return false;
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs) {
        return false;
    }
}
