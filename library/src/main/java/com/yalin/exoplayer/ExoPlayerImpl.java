package com.yalin.exoplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import com.yalin.exoplayer.ExoPlayerImplInternal.PlaybackInfo;
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

    private boolean pendingInitialSeek;
    private boolean playWhenReady;
    private int playbackState;
    private int pendingSeekAcks;
    private boolean isLoading;
    private Timeline timeline;
    private Object manifest;

    private PlaybackInfo playbackInfo;

    private int maskingWindowIndex;
    private long maskingWindowPositionMs;

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
        playbackInfo = new PlaybackInfo(0, 0);
        internalPlayer = new ExoPlayerImplInternal<>(renderers, trackSelector, loadControl,
                playWhenReady, eventHandler, playbackInfo);
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
        return playbackState;
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        prepare(mediaSource, true, true);
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetTimeline) {
        if (resetTimeline && (timeline != null || manifest != null)) {
            timeline = null;
            manifest = null;
            for (EventListener listener : listeners) {
                listener.onTimelineChanged(null, null);
            }
        }
        internalPlayer.prepare(mediaSource, resetPosition);
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        if (this.playWhenReady != playWhenReady) {
            this.playWhenReady = playWhenReady;
            internalPlayer.setPlayWhenReady(playWhenReady);
            for (EventListener listener : listeners) {
                listener.onPlayerStateChanged(playWhenReady, playbackState);
            }
        }
    }

    @Override
    public boolean getPlayWhenReady() {
        return playWhenReady;
    }

    @Override
    public boolean isLoading() {
        return isLoading;
    }

    @Override
    public void seekToDefaultPosition() {
        seekToDefaultPosition(getCurrentWindowIndex());
    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {
        if (timeline == null) {
            maskingWindowIndex = windowIndex;
            maskingWindowPositionMs = C.TIME_UNSET;
            pendingInitialSeek = true;
        } else {
            Assertions.checkIndex(windowIndex, 0, timeline.getWindowCount());
            pendingSeekAcks++;
            maskingWindowIndex = windowIndex;
            maskingWindowPositionMs = 0;
            internalPlayer.seekTo(timeline.getWindow(windowIndex, window).firstPeriodIndex, C.TIME_UNSET);
        }
    }

    @Override
    public void seekTo(long windowPositionMs) {
        seekTo(getCurrentWindowIndex(), windowPositionMs);
    }

    @Override
    public void seekTo(int windowIndex, long windowPositionMs) {
        if (windowPositionMs == C.TIME_UNSET) {
            seekToDefaultPosition(windowIndex);
        } else if (timeline == null) {
            maskingWindowIndex = windowIndex;
            maskingWindowPositionMs = windowPositionMs;
            pendingInitialSeek = true;
        } else {
            Assertions.checkIndex(windowIndex, 0, timeline.getWindowCount());
            pendingSeekAcks++;
            maskingWindowIndex = windowIndex;
            maskingWindowPositionMs = windowPositionMs;
            timeline.getWindow(windowIndex, window);
            int periodIndex = window.firstPeriodIndex;
            long periodPositionMs = window.getPositionInFirstPeriodMs() + windowPositionMs;
            long periodDurationMs = timeline.getPeriod(periodIndex, period).getDurationMs();
            while (periodDurationMs != C.TIME_UNSET && periodPositionMs >= periodDurationMs
                    && periodIndex < window.lastPeriodIndex) {
                periodPositionMs -= periodDurationMs;
                periodDurationMs = timeline.getPeriod(++periodIndex, period).getDurationMs();
            }
            internalPlayer.seekTo(periodIndex, C.msToUs(periodDurationMs));
            for (EventListener listener : listeners) {
                listener.onPositionDiscontinuity();
            }
        }
    }

    @Override
    public void stop() {
        internalPlayer.stop();
    }

    @Override
    public void release() {
        internalPlayer.release();
        eventHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void sendMessage(ExoPlayerMessage... messages) {
        internalPlayer.sendMessages(messages);
    }

    @Override
    public void blockingSendMessage(ExoPlayerMessage... messages) {
        internalPlayer.blockingSendMessages(messages);
    }

    @Override
    public Object getCurrentManifest() {
        return manifest;
    }

    @Override
    public Timeline getCurrentTimeline() {
        return timeline;
    }

    @Override
    public int getCurrentPeriodIndex() {
        return playbackInfo.periodIndex;
    }

    @Override
    public int getCurrentWindowIndex() {
        if (timeline == null || pendingSeekAcks > 0) {
            return maskingWindowIndex;
        } else {
            return timeline.getPeriod(playbackInfo.periodIndex, period).windowIndex;
        }
    }

    @Override
    public long getDuration() {
        if (timeline == null) {
            return C.TIME_UNSET;
        }
        return timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
    }

    @Override
    public long getCurrentPosition() {
        if (timeline == null || pendingSeekAcks > 0) {
            return maskingWindowPositionMs;
        } else {
            timeline.getPeriod(playbackInfo.periodIndex, period);
            return period.getPositionInWindowMs() + C.usToMs(playbackInfo.positionUs);
        }
    }

    @Override
    public long getBufferedPosition() {
        if (timeline == null || pendingSeekAcks > 0) {
            return maskingWindowPositionMs;
        } else {
            timeline.getPeriod(playbackInfo.periodIndex, period);
            return period.getPositionInWindowMs() + C.usToMs(playbackInfo.bufferedPositionUs);
        }
    }

    @Override
    public int getBufferedPercentage() {
        if (timeline == null) {
            return 0;
        }
        long bufferedPosition = getBufferedPosition();
        long duration = getDuration();
        return (bufferedPosition == C.TIME_UNSET || duration == C.TIME_UNSET) ? 0
                : (int) (duration == 0 ? 100 : (bufferedPosition * 100) / duration);
    }

    void handleEvent(Message msg) {
        switch (msg.what) {
            case ExoPlayerImplInternal.MSG_STATE_CHANGED: {
                playbackState = msg.arg1;
                for (EventListener listener : listeners) {
                    listener.onPlayerStateChanged(playWhenReady, playbackState);
                }
                break;
            }
            case ExoPlayerImplInternal.MSG_LOADING_CHANGED: {
                isLoading = msg.arg1 != 0;
                for (EventListener listener : listeners) {
                    listener.onLoadingChanged(isLoading);
                }
                break;
            }
            case ExoPlayerImplInternal.MSG_SEEK_ACK: {
                if (--pendingSeekAcks == 0) {
                    playbackInfo = (PlaybackInfo) msg.obj;
                    for (EventListener listener : listeners) {
                        listener.onPositionDiscontinuity();
                    }
                }
                break;
            }
            case ExoPlayerImplInternal.MSG_POSITION_DISCONTINUITY: {
                if (pendingSeekAcks == 0) {
                    playbackInfo = (PlaybackInfo) msg.obj;
                    for (EventListener listener : listeners) {
                        listener.onPositionDiscontinuity();
                    }
                }
                break;
            }
            case ExoPlayerImplInternal.MSG_SOURCE_INFO_REFRESHED: {
                //noinspection unchecked
                Pair<Timeline, Object> timelineAndManifest = (Pair<Timeline, Object>) msg.obj;
                timeline = timelineAndManifest.first;
                manifest = timelineAndManifest.second;
                if (pendingInitialSeek) {
                    pendingInitialSeek = false;
                    seekTo(maskingWindowIndex, maskingWindowPositionMs);
                }
                for (EventListener listener : listeners) {
                    listener.onTimelineChanged(timeline, manifest);
                }
                break;
            }
            case ExoPlayerImplInternal.MSG_ERROR: {
                ExoPlaybackException exception = (ExoPlaybackException) msg.obj;
                for (EventListener listener : listeners) {
                    listener.onPlayerError(exception);
                }
                break;
            }
        }
    }
}
