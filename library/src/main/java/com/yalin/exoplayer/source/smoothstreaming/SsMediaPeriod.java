package com.yalin.exoplayer.source.smoothstreaming;

import com.yalin.exoplayer.source.AdaptiveMediaSourceEventListener;
import com.yalin.exoplayer.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.yalin.exoplayer.source.MediaPeriod;
import com.yalin.exoplayer.source.SampleStream;
import com.yalin.exoplayer.source.TrackGroupArray;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.upstream.Allocator;
import com.yalin.exoplayer.upstream.LoaderErrorThrower;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

final class SsMediaPeriod implements MediaPeriod {

    public SsMediaPeriod(SsManifest manifest, SsChunkSource.Factory chunkSourceFactory,
                         int minLoadableRetryCount, EventDispatcher eventDispatcher,
                         LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator) {

    }

    public void updateManifest(SsManifest manifest) {

    }

    public void release() {

    }

    @Override
    public void prepare(Callback callback) {

    }

    @Override
    public void maybeThrowPrepareError() throws IOException {

    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return null;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        return 0;
    }

    @Override
    public long readDiscontinuity() {
        return 0;
    }

    @Override
    public long getBufferedPositionUs() {
        return 0;
    }

    @Override
    public long seekToUs(long positionUs) {
        return 0;
    }

    @Override
    public long getNextLoadPositionUs() {
        return 0;
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return false;
    }
}
