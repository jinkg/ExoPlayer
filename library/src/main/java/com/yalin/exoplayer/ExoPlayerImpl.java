package com.yalin.exoplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.yalin.exoplayer.source.MediaSource;
import com.yalin.exoplayer.trackslection.TrackSelector;
import com.yalin.exoplayer.util.Assertions;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

final class ExoPlayerImpl implements ExoPlayer {
    private final Handler eventHandler;
    private final ExoPlayerImplInternal<?> internalPlayer;
    private final CopyOnWriteArraySet<EventListener> listeners;
    private final Timeline.Window window;
    private final Timeline.Period period;

    private boolean playWhenReady;
    private int playbackState;

    public ExoPlayerImpl(Renderer[] renderers, TrackSelector<?> trackSelector,
                         LoadControl loadControl) {
        Assertions.checkNotNull(renderers);
        Assertions.checkState(renderers.length > 0);
        this.playWhenReady = false;
        this.playbackState = STATE_IDLE;
        this.listeners = new CopyOnWriteArraySet<>();
        window = new Timeline.Window();
        period = new Timeline.Period();
        eventHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                ExoPlayerImpl.this.handleEvent(msg);
            }
        };

        internalPlayer = new ExoPlayerImplInternal<>(renderers, eventHandler);
    }

    @Override
    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public int getPlaybackState() {
        return 0;
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        prepare(mediaSource, true, true);
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetTimeline) {

    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {

    }

    @Override
    public boolean getPlayWhenReady() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public void seekToDefaultPosition() {

    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {

    }

    @Override
    public void seekTo(long windowPositionMs) {

    }

    @Override
    public void seekTo(int windowIndex, long windowPositionMs) {

    }

    @Override
    public void stop() {

    }

    @Override
    public void release() {

    }

    @Override
    public void sendMessage(ExoPlayerMessage... messages) {

    }

    @Override
    public void blockingSendMessage(ExoPlayerMessage... messages) {

    }

    @Override
    public Object getCurrentManifest() {
        return null;
    }

    @Override
    public Timeline getCurrentTimeline() {
        return null;
    }

    @Override
    public int getCurrentPeriodIndex() {
        return 0;
    }

    @Override
    public int getCurrentWindowIndex() {
        return 0;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public long getCurrentPosition() {
        return 0;
    }

    @Override
    public long getBufferedPosition() {
        return 0;
    }

    @Override
    public int getBufferedPercentage() {
        return 0;
    }

    void handleEvent(Message msg) {

    }
}
