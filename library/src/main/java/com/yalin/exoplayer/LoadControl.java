package com.yalin.exoplayer;

import com.yalin.exoplayer.source.TrackGroupArray;
import com.yalin.exoplayer.trackslection.TrackSelections;
import com.yalin.exoplayer.upstream.Allocator;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface LoadControl {
    void onPrepared();

    void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
                          TrackSelections<?> trackSelections);

    void onStopped();

    void onReleased();

    Allocator getAllocator();

    boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering);

    boolean shouldContinueLoading(long bufferedDurationUs);
}
