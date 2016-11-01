package com.yalin.exoplayer;

import com.yalin.exoplayer.source.SampleStream;
import com.yalin.exoplayer.util.MediaClock;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface Renderer extends ExoPlayer.ExoPlayerComponent {
    int STATE_DISABLED = 0;

    int STATE_ENABLED = 1;

    int STATE_STARTED = 2;

    int getTrackType();

    RendererCapabilities getCapabilities();

    void setIndex(int index);

    MediaClock getMediaClock();

    int getState();

    void enable(Format[] formats, SampleStream stream, long positionUs, boolean joining,
                long offsetUs) throws ExoPlaybackException;

    void start() throws ExoPlaybackException;

    void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
            throws ExoPlaybackException;

    SampleStream getStream();

    boolean hasReadStreamToEnd();

    void setCurrentStreamIsFinal();

    void maybeThrowStreamError() throws IOException;

    void resetPosition(long positionUs) throws ExoPlaybackException;

    void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException;

    boolean isReady();

    boolean isEnded();

    void stop() throws ExoPlaybackException;

    void disable();

}
