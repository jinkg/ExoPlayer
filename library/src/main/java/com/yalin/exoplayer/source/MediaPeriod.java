package com.yalin.exoplayer.source;

import com.yalin.exoplayer.trackslection.TrackSelection;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface MediaPeriod extends SequenceableLoader {
    interface Callback extends SequenceableLoader.Callback<MediaSource {
        void onPrepared(MediaPeriod mediaPeriod);
    }

    void prepare(Callback callback);

    void maybeThrowPrepareError() throws IOException;

    TrackGroupArray getTrackGroups();

    long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
                      SampleStream[] streams, boolean[] streamResetFlags, long positionUs);

    long readDiscontinuity();

    long getBufferedPositionUs();

    long seekToUs(long positionUs);
}
