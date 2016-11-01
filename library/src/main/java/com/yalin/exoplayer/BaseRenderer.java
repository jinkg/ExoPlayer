package com.yalin.exoplayer;

import com.yalin.exoplayer.decoder.DecoderInputBuffer;
import com.yalin.exoplayer.source.SampleStream;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.MediaClock;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class BaseRenderer implements Renderer, RendererCapabilities {
    private final int trackType;

    private int index;
    private int state;
    private SampleStream stream;
    private long streamOffsetUs;
    private boolean readEndOfStream;
    private boolean streamIsFinal;

    public BaseRenderer(int trackType) {
        this.trackType = trackType;
        readEndOfStream = true;
    }

    @Override
    public int getTrackType() {
        return trackType;
    }

    @Override
    public RendererCapabilities getCapabilities() {
        return this;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public MediaClock getMediaClock() {
        return null;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void enable(Format[] formats, SampleStream stream, long positionUs,
                       boolean joining, long offsetUs) throws ExoPlaybackException {
        Assertions.checkState(state == STATE_DISABLED);
        state = STATE_ENABLED;
        onEnabled(joining);
        replaceStream(formats, stream, offsetUs);
        onPositionReset(positionUs, joining);
    }

    @Override
    public void start() throws ExoPlaybackException {
        Assertions.checkState(state == STATE_ENABLED);
        state = STATE_STARTED;
        onStarted();
    }

    @Override
    public void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
            throws ExoPlaybackException {
        Assertions.checkState(!streamIsFinal);
        this.stream = stream;
        readEndOfStream = false;
        streamOffsetUs = offsetUs;
        onStreamChanged(formats);
    }

    @Override
    public SampleStream getStream() {
        return stream;
    }

    @Override
    public boolean hasReadStreamToEnd() {
        return readEndOfStream;
    }

    @Override
    public void setCurrentStreamIsFinal() {
        streamIsFinal = true;
    }

    @Override
    public void maybeThrowStreamError() throws IOException {
        stream.maybeThrowError();
    }

    @Override
    public void resetPosition(long positionUs) throws ExoPlaybackException {
        streamIsFinal = false;
        onPositionReset(positionUs, false);
    }

    @Override
    public void stop() throws ExoPlaybackException {
        Assertions.checkState(state == STATE_STARTED);
        state = STATE_ENABLED;
        onStopped();
    }

    @Override
    public void disable() {
        Assertions.checkState(state == STATE_ENABLED);
        state = STATE_DISABLED;
        onDisabled();
        stream = null;
        streamIsFinal = false;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
        return ADAPTIVE_NOT_SUPPORTED;
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {

    }

    protected void onEnabled(boolean joining) throws ExoPlaybackException {

    }

    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {

    }

    protected void onStarted() throws ExoPlaybackException {

    }

    protected void onStopped() throws ExoPlaybackException {

    }

    protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {

    }

    protected void onDisabled() {

    }

    protected final int getIndex() {
        return index;
    }

    protected final int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer) {
        int result = stream.readData(formatHolder, buffer);
        if (result == C.RESULT_BUFFER_READ) {
            if (buffer.isEndOfStream()) {
                readEndOfStream = true;
                return streamIsFinal ? C.RESULT_BUFFER_READ : C.RESULT_NOTING_READ;
            }
            buffer.timeUs += streamOffsetUs;
        }
        return result;
    }

    protected final boolean isSourceReady() {
        return readEndOfStream ? streamIsFinal : stream.isReady();
    }

    protected void skipToKeyframeBefore(long timeUs) {
        stream.skipToKeyframeBefor(timeUs);
    }
}
