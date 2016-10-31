package com.yalin.exoplayer;

import com.yalin.exoplayer.source.MediaSource;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface ExoPlayer {
    interface EventListener {
        void onLoadingChanged(boolean isLoading);

        void onPlayerStateChanged(boolean playWhenReady, int playbackState);

        void onTimelineChanged(Timeline timeline, Object manifest);

        void onPlayerError(ExoPlaybackException error);

        void onPositionDiscontinuity();
    }

    interface ExoPlayerComponent {
        void handleMessage(int messageType, Object message) throws ExoPlaybackException;
    }

    final class ExoPlayerMessage {
        public final ExoPlayerComponent target;

        public final int messageType;

        public final Object message;

        public ExoPlayerMessage(ExoPlayerComponent target, int messageType, Object message) {
            this.target = target;
            this.messageType = messageType;
            this.message = message;
        }
    }

    int STATE_IDLE = 1;
    int STATE_BUFFERING = 2;
    int STATE_READY = 3;
    int STATE_ENDED = 4;

    void addListener(EventListener listener);

    void removeListener(EventListener listener);

    int getPlaybackState();

    void prepare(MediaSource mediaSource);

    void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetTimeline);

    void setPlayWhenReady(boolean playWhenReady);

    boolean getPlayWhenReady();

    boolean isLoading();

    void seekToDefaultPosition();

    void seekToDefaultPosition(int windowIndex);

    void seekTo(long windowPositionMs);

    void seekTo(int windowIndex, long windowPositionMs);

    void stop();

    void release();

    void sendMessage(ExoPlayerMessage... messages);

    void blockingSendMessage(ExoPlayerMessage... messages);

    Object getCurrentManifest();

    Timeline getCurrentTimeline();

    int getCurrentPeriodIndex();

    int getCurrentWindowIndex();

    long getDuration();

    long getCurrentPosition();

    long getBufferedPosition();

    int getBufferedPercentage();


}
