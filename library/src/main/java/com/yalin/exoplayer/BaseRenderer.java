package com.yalin.exoplayer;

import com.yalin.exoplayer.source.SampleStream;
import com.yalin.exoplayer.util.MediaClock;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class BaseRenderer implements Renderer, RendererCapabilities {
    @Override
    public int getTrackType() {
        return 0;
    }

    @Override
    public RendererCapabilities getCapabilities() {
        return null;
    }

    @Override
    public void setIndex(int index) {

    }

    @Override
    public MediaClock getMediaClock() {
        return null;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public void enable(Format[] formats, SampleStream stream, long positionUs, boolean joining, long offsetUs) throws ExoPlaybackException {

    }

    @Override
    public void start() throws ExoPlaybackException {

    }

    @Override
    public void replaceStream(Format[] formats, SampleStream stream, long offsetUs) throws ExoPlaybackException {

    }

    @Override
    public SampleStream getStream() {
        return null;
    }

    @Override
    public boolean hasReadStreamToEnd() {
        return false;
    }

    @Override
    public void setCurrentStreamIsFinal() {

    }

    @Override
    public void maybeThrowStreamError() throws IOException {

    }

    @Override
    public void resetPosition(long positionUs) throws ExoPlaybackException {

    }

    @Override
    public void stop() throws ExoPlaybackException {

    }

    @Override
    public void disable() {

    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
        return ADAPTIVE_NOT_SUPPORTED;
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {

    }
}
