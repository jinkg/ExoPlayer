package com.yalin.exoplayer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.yalin.exoplayer.ExoPlayer.ExoPlayerMessage;
import com.yalin.exoplayer.source.MediaPeriod;
import com.yalin.exoplayer.source.MediaSource;
import com.yalin.exoplayer.source.SampleStream;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.trackslection.TrackSelections;
import com.yalin.exoplayer.trackslection.TrackSelector;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.MediaClock;
import com.yalin.exoplayer.util.PriorityHandlerThread;
import com.yalin.exoplayer.util.StandaloneMediaClock;
import com.yalin.exoplayer.util.TraceUtil;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

final class ExoPlayerImplInternal<T> implements Handler.Callback, TrackSelector.InvalidationListener, MediaSource.Listener,
        MediaPeriod.Callback {

    public static final class PlaybackInfo {
        public final int periodIndex;
        public final long startPositionUs;

        public volatile long positionUs;
        public volatile long bufferedPositionUs;

        public PlaybackInfo(int periodIndex, long startPositionUs) {
            this.periodIndex = periodIndex;
            this.startPositionUs = startPositionUs;
            positionUs = startPositionUs;
            bufferedPositionUs = startPositionUs;
        }
    }

    private static final String TAG = "ExoPlayerImplInternal";

    private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
    private static final int RENDERING_INTERVAL_MS = 10;
    private static final int IDLE_INTERVAL_MS = 1000;

    // External messages
    public static final int MSG_STATE_CHANGED = 1;
    public static final int MSG_LOADING_CHANGED = 2;
    public static final int MSG_SEEK_ACK = 3;
    public static final int MSG_POSITION_DISCONTINUITY = 4;
    public static final int MSG_SOURCE_INFO_REFRESHED = 5;
    public static final int MSG_ERROR = 6;

    // Internal messages
    private static final int MSG_PREPARE = 0;
    private static final int MSG_SET_PLAY_WHEN_READY = 1;
    private static final int MSG_DO_SOME_WORK = 2;
    private static final int MSG_SEEK_TO = 3;
    private static final int MSG_STOP = 4;
    private static final int MSG_RELEASE = 5;
    private static final int MSG_REFRESH_SOURCE_INFO = 6;
    private static final int MSG_PERIOD_PREPARED = 7;
    private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 8;
    private static final int MSG_TRACK_SELECTION_INVALIDATED = 9;
    private static final int MSG_CUSTOM = 10;

    private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

    private final Renderer[] renderers;
    private final RendererCapabilities[] rendererCapabilities;
    private final TrackSelector<T> trackSelector;
    private final LoadControl loadControl;
    private final StandaloneMediaClock standaloneMediaClock;
    private final Handler handler;
    private final HandlerThread internalPlaybackThread;
    private final Handler eventHandler;
    private final Timeline.Window window;
    private final Timeline.Period period;

    private PlaybackInfo playbackInfo;
    private Renderer rendererMediaClockSource;
    private Renderer[] enabledRenderers;
    private MediaClock rendererMediaClock;
    private MediaSource mediaSource;
    private boolean released;
    private boolean playWhenReady;
    private boolean rebuffering;
    private boolean isLoading;
    private int state;
    private int customMessagesSent;
    private int customMessagesProcessed;
    private long elapsedRealtimeUs;

    private long rendererPositionUs;

    private boolean isTimelineReady;
    private boolean isTimelineEnded;
    private int bufferAheadPeriodCount;
    private MediaPeriodHolder<T> playingPeriodHolder;
    private MediaPeriodHolder<T> readingPeriodHolder;
    private MediaPeriodHolder<T> loadingPeriodHolder;

    private Timeline timeline;

    public ExoPlayerImplInternal(Renderer[] renderers, TrackSelector<T> trackSelector,
                                 LoadControl loadControl, boolean playWhenReady, Handler eventHandler,
                                 PlaybackInfo playbackInfo) {
        this.renderers = renderers;
        this.trackSelector = trackSelector;
        this.loadControl = loadControl;
        this.playWhenReady = playWhenReady;
        this.eventHandler = eventHandler;
        this.state = ExoPlayer.STATE_IDLE;
        this.playbackInfo = playbackInfo;

        rendererCapabilities = new RendererCapabilities[renderers.length];
        for (int i = 0; i < renderers.length; i++) {
            renderers[i].setIndex(i);
            rendererCapabilities[i] = renderers[i].getCapabilities();
        }
        standaloneMediaClock = new StandaloneMediaClock();
        enabledRenderers = new Renderer[0];
        window = new Timeline.Window();
        period = new Timeline.Period();
        trackSelector.init(this);

        internalPlaybackThread = new PriorityHandlerThread("ExoPlayerImplInternal:Handler",
                Process.THREAD_PRIORITY_AUDIO);
        internalPlaybackThread.start();
        handler = new Handler(internalPlaybackThread.getLooper(), this);
    }

    public void prepare(MediaSource mediaSource, boolean resetPosition) {
        handler.obtainMessage(MSG_PREPARE, resetPosition ? 1 : 0, 0, mediaSource)
                .sendToTarget();
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0)
                .sendToTarget();
    }

    public void seekTo(int periodIndex, long positionUs) {
        handler.obtainMessage(MSG_SEEK_TO, periodIndex, 0, positionUs)
                .sendToTarget();
    }

    public void stop() {
        handler.sendEmptyMessage(MSG_STOP);
    }

    public void sendMessages(ExoPlayerMessage... messages) {
        if (released) {
            Log.w(TAG, "Ignoring message sent after release.");
            return;
        }
        customMessagesSent++;
        handler.obtainMessage(MSG_CUSTOM, messages).sendToTarget();
    }

    public synchronized void blockingSendMessages(ExoPlayerMessage... messages) {
        if (released) {
            Log.w(TAG, "Ignoring messages sent after release.");
            return;
        }
        int messageNumber = customMessagesSent++;
        handler.obtainMessage(MSG_CUSTOM, messages).sendToTarget();
        while (customMessagesProcessed <= messageNumber) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void release() {
        if (released) {
            return;
        }
        handler.sendEmptyMessage(MSG_RELEASE);
        while (!released) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        internalPlaybackThread.quit();
    }

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
        handler.obtainMessage(MSG_PERIOD_PREPARED, mediaPeriod).sendToTarget();
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod source) {
        handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
    }

    @Override
    public void onTrackSelectionsInvalidated() {
        handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
    }

    @Override
    public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        handler.obtainMessage(MSG_REFRESH_SOURCE_INFO, Pair.create(timeline, manifest)).sendToTarget();
    }

    @Override
    public boolean handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case MSG_PREPARE: {
                    prepareInternal((MediaSource) msg.obj, msg.arg1 != 0);
                    return true;
                }
                case MSG_SET_PLAY_WHEN_READY: {
                    setPlayWhenReadyInternal(msg.arg1 != 0);
                    return true;
                }
                case MSG_DO_SOME_WORK: {
                    doSomeWork();
                    return true;
                }
                case MSG_SEEK_TO: {
                    seekToInternal(msg.arg1, (Long) msg.obj);
                    return true;
                }
                case MSG_STOP: {
                    stopInternal();
                    return true;
                }
                case MSG_RELEASE: {
                    releaseInternal();
                    return true;
                }
                case MSG_PERIOD_PREPARED: {
                    handlePeriodPrepared((MediaPeriod) msg.obj);
                    return true;
                }
                case MSG_REFRESH_SOURCE_INFO: {
                    //noinspection unchecked
                    handleSourceInfoRefreshed((Pair<Timeline, Object>) msg.obj);
                    return true;
                }
                case MSG_SOURCE_CONTINUE_LOADING_REQUESTED: {
                    handleContinueLoadingRequested((MediaPeriod) msg.obj);
                    return true;
                }
                case MSG_TRACK_SELECTION_INVALIDATED: {
                    reselectTracksInternal();
                    return true;
                }
                case MSG_CUSTOM: {
                    sendMessagesInternal((ExoPlayerMessage[]) msg.obj);
                    return true;
                }
                default:
                    return false;
            }
        } catch (ExoPlaybackException e) {
            Log.e(TAG, "Renderer error.", e);
            eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Source error.", e);
            eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForSource(e)).sendToTarget();
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Internal runtime error.", e);
            eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForUnexpected(e)).sendToTarget();
            return true;
        }
    }

    private void setState(int state) {
        if (this.state != state) {
            this.state = state;
            eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
        }
    }

    private void setIsLoading(boolean isLoading) {
        if (this.isLoading != isLoading) {
            this.isLoading = isLoading;
            eventHandler.obtainMessage(MSG_LOADING_CHANGED, isLoading ? 1 : 0, 0).sendToTarget();
        }
    }

    private void prepareInternal(MediaSource mediaSource, boolean resetPosition)
            throws ExoPlaybackException {
        resetInternal();
        loadControl.onPrepared();
        if (resetPosition) {
            playbackInfo = new PlaybackInfo(0, C.TIME_UNSET);
        }
        this.mediaSource = mediaSource;
        mediaSource.prepareSource(this);
        setState(ExoPlayer.STATE_BUFFERING);
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }

    private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
        rebuffering = false;
        this.playWhenReady = playWhenReady;
        if (!playWhenReady) {
            stopRenderers();
            updatePlaybackPositions();
        } else {
            if (state == ExoPlayer.STATE_READY) {
                startRenderers();
                handler.sendEmptyMessage(MSG_DO_SOME_WORK);
            } else if (state == ExoPlayer.STATE_BUFFERING) {
                handler.sendEmptyMessage(MSG_DO_SOME_WORK);
            }
        }
    }

    private void startRenderers() throws ExoPlaybackException {
        rebuffering = false;
        standaloneMediaClock.start();
        for (Renderer renderer : enabledRenderers) {
            renderer.start();
        }
    }

    private void stopRenderers() throws ExoPlaybackException {
        standaloneMediaClock.stop();
        for (Renderer renderer : enabledRenderers) {
            ensureStopped(renderer);
        }
    }

    private void updatePlaybackPositions() throws ExoPlaybackException {
        if (playingPeriodHolder == null) {
            return;
        }

        long periodPositionUs = playingPeriodHolder.mediaPeriod.readDiscontinuity();
        if (periodPositionUs != C.TIME_UNSET) {
            resetRendererPosition(periodPositionUs);
        } else {
            if (rendererMediaClockSource != null && !rendererMediaClockSource.isEnded()) {
                rendererPositionUs = rendererMediaClock.getPositionUs();
                standaloneMediaClock.setPositionUs(rendererPositionUs);
            } else {
                rendererPositionUs = standaloneMediaClock.getPositionUs();
            }
            periodPositionUs = rendererPositionUs - playingPeriodHolder.rendererPositionOffsetUs;
        }
        playbackInfo.positionUs = periodPositionUs;
        elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

        long bufferedPositionUs = enabledRenderers.length == 0 ? C.TIME_END_OF_SOURCE
                : playingPeriodHolder.mediaPeriod.getBufferedPositionUs();
        playbackInfo.bufferedPositionUs = bufferedPositionUs == C.TIME_END_OF_SOURCE
                ? timeline.getPeriod(playingPeriodHolder.index, period).getDurationUs()
                : bufferedPositionUs;
    }

    private void doSomeWork() throws ExoPlaybackException, IOException {
        long operationStartTimeMs = SystemClock.elapsedRealtime();

        updatePeriods();
        if (playingPeriodHolder == null) {
            maybeThrowPeriodPrepareError();
            scheduleNextWork(operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
            return;
        }

        TraceUtil.beginSection("doSomeWork");

        updatePlaybackPositions();
        boolean allRenderersEnded = true;
        boolean allRenderersReadyOrEnded = true;
        for (Renderer renderer : enabledRenderers) {
            renderer.render(rendererPositionUs, elapsedRealtimeUs);
            allRenderersEnded = allRenderersEnded && renderer.isEnded();
            boolean rendererReadyOrEnded = renderer.isReady();
            if (!rendererReadyOrEnded) {
                renderer.maybeThrowStreamError();
            }
            allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
        }

        if (!allRenderersReadyOrEnded) {
            maybeThrowPeriodPrepareError();
        }

        long playingPeriodDurationUs = timeline.getPeriod(playingPeriodHolder.index, period)
                .getDurationUs();
        if (allRenderersEnded
                && (playingPeriodDurationUs == C.TIME_UNSET
                || playingPeriodDurationUs <= playbackInfo.positionUs)
                && isTimelineEnded) {
            setState(ExoPlayer.STATE_ENDED);
            stopRenderers();
        } else if (state == ExoPlayer.STATE_BUFFERING) {
            if ((enabledRenderers.length > 0
                    ? (allRenderersReadyOrEnded && haveSufficientBuffer(rebuffering)) : isTimelineReady)) {
                setState(ExoPlayer.STATE_READY);
                if (playWhenReady) {
                    startRenderers();
                }
            }
        } else if (state == ExoPlayer.STATE_READY
                && (enabledRenderers.length > 0 ? !allRenderersReadyOrEnded : !isTimelineReady)) {
            rebuffering = playWhenReady;
            setState(ExoPlayer.STATE_BUFFERING);
            stopRenderers();
        }

        if (state == ExoPlayer.STATE_BUFFERING) {
            for (Renderer renderer : enabledRenderers) {
                renderer.maybeThrowStreamError();
            }
        }

        if ((playWhenReady && state == ExoPlayer.STATE_READY) || state == ExoPlayer.STATE_BUFFERING) {
            scheduleNextWork(operationStartTimeMs, RENDERING_INTERVAL_MS);
        } else if (enabledRenderers.length != 0) {
            scheduleNextWork(operationStartTimeMs, IDLE_INTERVAL_MS);
        } else {
            handler.removeMessages(MSG_DO_SOME_WORK);
        }
        TraceUtil.endSection();
    }

    private void scheduleNextWork(long thisOperationStartTimeMs, long intervalMs) {
        handler.removeMessages(MSG_DO_SOME_WORK);
        long nextOperationStartTimeMs = thisOperationStartTimeMs + intervalMs;
        long nextOperationDelayMs = nextOperationStartTimeMs - SystemClock.elapsedRealtime();
        if (nextOperationDelayMs <= 0) {
            handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        } else {
            handler.sendEmptyMessageDelayed(MSG_DO_SOME_WORK, nextOperationDelayMs);
        }
    }

    private void seekToInternal(int periodIndex, long periodPositionUs) throws ExoPlaybackException {
        try {
            if (periodPositionUs == C.TIME_UNSET && timeline != null
                    && periodIndex < timeline.getPeriodCount()) {
                Pair<Integer, Long> defaultPosition = getDefaultPosition(periodIndex);
                periodIndex = defaultPosition.first;
                periodPositionUs = defaultPosition.second;
            }

            if (periodIndex == playbackInfo.periodIndex
                    && ((periodPositionUs == C.TIME_UNSET && playbackInfo.positionUs == C.TIME_UNSET)
                    || ((periodPositionUs / 1000) == (playbackInfo.positionUs / 1000)))) {
                return;
            }
            periodPositionUs = seekToPeriodPosition(periodIndex, periodPositionUs);
        } finally {
            playbackInfo = new PlaybackInfo(periodIndex, periodPositionUs);
            eventHandler.obtainMessage(MSG_SEEK_ACK, playbackInfo).sendToTarget();
        }
    }

    private long seekToPeriodPosition(int periodIndex, long periodPositionUs)
            throws ExoPlaybackException {
        if (mediaSource == null) {
            if (periodPositionUs != C.TIME_UNSET) {
                resetRendererPosition(periodPositionUs);
            }
            return periodPositionUs;
        }
        stopRenderers();
        rebuffering = false;
        setState(ExoPlayer.STATE_BUFFERING);

        if (periodPositionUs == C.TIME_UNSET || (readingPeriodHolder != playingPeriodHolder
                && (periodIndex == playingPeriodHolder.index
                || periodIndex == readingPeriodHolder.index))) {
            periodIndex = C.INDEX_UNSET;
        }

        MediaPeriodHolder<T> newPlayingPeriodHolder = null;
        if (playingPeriodHolder == null) {
            if (loadingPeriodHolder != null) {
                loadingPeriodHolder.release();
            }
        } else {
            MediaPeriodHolder<T> periodHolder = playingPeriodHolder;
            while (periodHolder != null) {
                if (periodHolder.index == periodIndex && periodHolder.prepared) {
                    newPlayingPeriodHolder = periodHolder;
                } else {
                    periodHolder.release();
                }
                periodHolder = periodHolder.next;
            }
        }

        if (newPlayingPeriodHolder != playingPeriodHolder) {
            for (Renderer renderer : enabledRenderers) {
                renderer.disable();
            }
            enabledRenderers = new Renderer[0];
            rendererMediaClock = null;
            rendererMediaClockSource = null;
        }

        bufferAheadPeriodCount = 0;
        if (newPlayingPeriodHolder != null) {
            newPlayingPeriodHolder.next = null;
            setPlayingPeriodHolder(newPlayingPeriodHolder);
            updateTimelineState();
            readingPeriodHolder = playingPeriodHolder;
            loadingPeriodHolder = playingPeriodHolder;
            if (playingPeriodHolder.hasEnabledTracks) {
                periodPositionUs = playingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
            }
            resetRendererPosition(periodPositionUs);
            maybeContinueLoading();
        } else {
            playingPeriodHolder = null;
            readingPeriodHolder = null;
            loadingPeriodHolder = null;
            if (periodPositionUs != C.TIME_UNSET) {
                resetRendererPosition(periodPositionUs);
            }
        }
        updatePlaybackPositions();
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        return periodPositionUs;
    }

    private void stopInternal() {
        resetInternal();
        loadControl.onStopped();
        setState(ExoPlayer.STATE_IDLE);
    }

    private void releaseInternal() {
        resetInternal();
        loadControl.onReleased();
        setState(ExoPlayer.STATE_IDLE);
        synchronized (this) {
            released = true;
            notifyAll();
        }
    }

    private void handlePeriodPrepared(MediaPeriod period) throws ExoPlaybackException {
        if (loadingPeriodHolder == null || loadingPeriodHolder.mediaPeriod != period) {
            return;
        }
        loadingPeriodHolder.handlePrepared(loadingPeriodHolder.startPositionUs, loadControl);
        if (playingPeriodHolder == null) {
            readingPeriodHolder = loadingPeriodHolder;
            setPlayingPeriodHolder(readingPeriodHolder);
            if (playbackInfo.startPositionUs == C.TIME_UNSET) {
                playbackInfo = new PlaybackInfo(playingPeriodHolder.index,
                        playingPeriodHolder.startPositionUs);
                resetRendererPosition(playbackInfo.startPositionUs);
                updatePlaybackPositions();
                eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
            }
            updateTimelineState();
        }
        maybeContinueLoading();
    }

    private void handleSourceInfoRefreshed(Pair<Timeline, Object> timelineAndManifest)
            throws ExoPlaybackException, IOException {
        eventHandler.obtainMessage(MSG_SOURCE_INFO_REFRESHED, timelineAndManifest).sendToTarget();
        Timeline oldTimeline = this.timeline;
        this.timeline = timelineAndManifest.first;

        if (playingPeriodHolder != null) {
            int index = timeline.getIndexOfPeriod(playingPeriodHolder.uid);
            if (index == C.INDEX_UNSET) {
                attemptRestart(timeline, oldTimeline, playingPeriodHolder.index);
                return;
            }

            timeline.getPeriod(index, period, true);
            playingPeriodHolder.setIndex(timeline, timeline.getWindow(period.windowIndex, window),
                    index);

            MediaPeriodHolder<T> previousPeriodHolder = playingPeriodHolder;
            boolean seenReadingPeriod = false;
            bufferAheadPeriodCount = 0;
            while (previousPeriodHolder.next != null) {
                MediaPeriodHolder<T> periodHolder = previousPeriodHolder.next;
                index++;
                timeline.getPeriod(index, period, true);
                if (!periodHolder.uid.equals(period.uid)) {
                    if (!seenReadingPeriod) {
                        index = playingPeriodHolder.index;
                        releasePeriodHoldersFrom(playingPeriodHolder);
                        playingPeriodHolder = null;
                        readingPeriodHolder = null;
                        loadingPeriodHolder = null;
                        long newPositionUs = seekToPeriodPosition(index, playbackInfo.positionUs);
                        if (newPositionUs != playbackInfo.positionUs) {
                            playbackInfo = new PlaybackInfo(index, newPositionUs);
                            eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY);
                        }
                        return;
                    }
                }
                bufferAheadPeriodCount++;
                int windowIndex = timeline.getPeriod(index, period).windowIndex;
                periodHolder.setIndex(timeline, timeline.getWindow(windowIndex, window), index);
                if (periodHolder == readingPeriodHolder) {
                    seenReadingPeriod = true;
                }
                previousPeriodHolder = periodHolder;
            }
        } else if (loadingPeriodHolder != null) {
            Object uid = loadingPeriodHolder.uid;
            int index = timeline.getIndexOfPeriod(uid);
            if (index == C.INDEX_UNSET) {
                attemptRestart(timeline, oldTimeline, loadingPeriodHolder.index);
                return;
            } else {
                int windowIndex = timeline.getPeriod(index, this.period).windowIndex;
                loadingPeriodHolder.setIndex(timeline, timeline.getWindow(windowIndex, window),
                        index);
            }
        }

        if (oldTimeline != null) {
            int newPlayingIndex = playingPeriodHolder != null ? playingPeriodHolder.index
                    : loadingPeriodHolder != null ? loadingPeriodHolder.index : C.INDEX_UNSET;
            if (newPlayingIndex != C.INDEX_UNSET
                    && newPlayingIndex != playbackInfo.periodIndex) {
                playbackInfo = new PlaybackInfo(newPlayingIndex, playbackInfo.positionUs);
                updatePlaybackPositions();
                eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
            }
        }

    }

    private void attemptRestart(Timeline newTimeline, Timeline oldTimeline,
                                int oldPeriodIndex) throws ExoPlaybackException {
        int newPeriodIndex = C.INDEX_UNSET;
        while (newPeriodIndex == C.INDEX_UNSET
                && oldPeriodIndex < oldTimeline.getWindowCount() - 1) {
            newPeriodIndex = newTimeline.getIndexOfPeriod(oldTimeline.getPeriod(++oldPeriodIndex, period, true).uid);
        }
        if (newPeriodIndex == C.INDEX_UNSET) {
            stopInternal();
            return;
        }

        releasePeriodHoldersFrom(playingPeriodHolder != null ? playingPeriodHolder
                : loadingPeriodHolder);
        bufferAheadPeriodCount = 0;
        playingPeriodHolder = null;
        readingPeriodHolder = null;
        loadingPeriodHolder = null;

        Pair<Integer, Long> defaultPosition = getDefaultPosition(newPeriodIndex);
        newPeriodIndex = defaultPosition.first;
        long newPlayingPositionUs = defaultPosition.second;

        playbackInfo = new PlaybackInfo(newPeriodIndex, newPlayingPositionUs);
        eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
    }

    private void handleContinueLoadingRequested(MediaPeriod period) {
        if (loadingPeriodHolder == null || loadingPeriodHolder.mediaPeriod != period) {
            return;
        }
        maybeContinueLoading();
    }

    private void reselectTracksInternal() throws ExoPlaybackException {
        if (playingPeriodHolder == null) {
            return;
        }
        MediaPeriodHolder<T> periodHolder = playingPeriodHolder;
        boolean selectionsChangedForReadPeriod = true;
        while (true) {
            if (periodHolder == null || !periodHolder.prepared) {
                return;
            }
            if (periodHolder.selectTracks()) {
                break;
            }
            if (periodHolder == readingPeriodHolder) {
                selectionsChangedForReadPeriod = false;
            }
            periodHolder = periodHolder.next;
        }

        if (selectionsChangedForReadPeriod) {
            boolean recreateStreams = readingPeriodHolder != playingPeriodHolder;
            releasePeriodHoldersFrom(playingPeriodHolder.next);
            playingPeriodHolder.next = null;
            readingPeriodHolder = playingPeriodHolder;
            loadingPeriodHolder = playingPeriodHolder;
            bufferAheadPeriodCount = 0;

            boolean[] streamResetFlags = new boolean[renderers.length];
            long periodPositionUs = playingPeriodHolder.updatePeriodTrackSelection(
                    playbackInfo.positionUs, loadControl, recreateStreams, streamResetFlags);
            if (periodPositionUs != playbackInfo.positionUs) {
                playbackInfo.positionUs = periodPositionUs;
                resetRendererPosition(periodPositionUs);
            }

            int enabledRendererCount = 0;
            boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
            for (int i = 0; i < renderers.length; i++) {
                Renderer renderer = renderers[i];
                rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
                SampleStream sampleStream = playingPeriodHolder.sampleStreams[i];
                if (sampleStream != null) {
                    enabledRendererCount++;
                }
                if (rendererWasEnabledFlags[i]) {
                    if (sampleStream != renderer.getStream()) {
                        if (renderer == rendererMediaClockSource) {
                            if (sampleStream == null) {
                                standaloneMediaClock.setPositionUs(rendererMediaClock.getPositionUs());
                            }
                            rendererMediaClock = null;
                            rendererMediaClockSource = null;
                        }
                        ensureStopped(renderer);
                        renderer.disable();
                    } else if (streamResetFlags[i]) {
                        renderer.resetPosition(playbackInfo.positionUs);
                    }
                }
            }
            trackSelector.onSelectionActivated(playingPeriodHolder.trackSelections);
            enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
        } else {
            loadingPeriodHolder = periodHolder;
            periodHolder = loadingPeriodHolder.next;
            while (periodHolder != null) {
                periodHolder.release();
                periodHolder = periodHolder.next;
                bufferAheadPeriodCount--;
            }
            loadingPeriodHolder.next = null;
            long loadingPeriodPositionUs = Math.max(0,
                    rendererPositionUs - loadingPeriodHolder.rendererPositionOffsetUs);
            loadingPeriodHolder.updatePeriodTrackSelection(loadingPeriodPositionUs, loadControl, false);
        }
        maybeContinueLoading();
        updatePlaybackPositions();
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }

    private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {
        try {
            for (ExoPlayerMessage message : messages) {
                message.target.handleMessage(message.messageType, message.message);
            }
            if (mediaSource != null) {
                handler.sendEmptyMessage(MSG_DO_SOME_WORK);
            }
        } finally {
            synchronized (this) {
                customMessagesProcessed++;
                notifyAll();
            }
        }
    }

    private void resetInternal() {
        handler.removeMessages(MSG_DO_SOME_WORK);
        rebuffering = false;
        standaloneMediaClock.stop();
        rendererMediaClock = null;
        rendererMediaClockSource = null;
        for (Renderer renderer : enabledRenderers) {
            try {
                ensureStopped(renderer);
                renderer.disable();
            } catch (ExoPlaybackException | RuntimeException e) {
                Log.e(TAG, "Stop failed.", e);
            }
        }
        enabledRenderers = new Renderer[0];
        releasePeriodHoldersFrom(playingPeriodHolder != null ? playingPeriodHolder
                : loadingPeriodHolder);
        if (mediaSource != null) {
            mediaSource.releaseSource();
            mediaSource = null;
        }
        isTimelineReady = false;
        isTimelineEnded = false;
        playingPeriodHolder = null;
        readingPeriodHolder = null;
        loadingPeriodHolder = null;
        timeline = null;
        bufferAheadPeriodCount = 0;
        setIsLoading(false);
    }

    private void ensureStopped(Renderer renderer) throws ExoPlaybackException {
        if (renderer.getState() == Renderer.STATE_STARTED) {
            renderer.stop();
        }
    }

    private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
        long periodOffsetUs = playingPeriodHolder == null ? 0
                : playingPeriodHolder.rendererPositionOffsetUs;
        rendererPositionUs = periodOffsetUs + periodPositionUs;
        standaloneMediaClock.setPositionUs(rendererPositionUs);
        for (Renderer renderer : enabledRenderers) {
            renderer.resetPosition(rendererPositionUs);
        }
    }

    private void updatePeriods() throws ExoPlaybackException, IOException {
        if (timeline == null) {
            mediaSource.maybeThrowSourceInfoRefreshError();
            return;
        }

        if (loadingPeriodHolder == null
                || (loadingPeriodHolder.isFullyBuffered() && !loadingPeriodHolder.isLast
                && bufferAheadPeriodCount < MAXIMUM_BUFFER_AHEAD_PERIODS)) {
            int newLoadingPeriodIndex = loadingPeriodHolder == null ? playbackInfo.periodIndex
                    : loadingPeriodHolder.index + 1;
            if (newLoadingPeriodIndex >= timeline.getWindowCount()) {
                mediaSource.maybeThrowSourceInfoRefreshError();
            } else {
                int windowIndex = timeline.getPeriod(newLoadingPeriodIndex, period).windowIndex;
                boolean isFirstPeriodInWindow = newLoadingPeriodIndex
                        == timeline.getWindow(windowIndex, window).firstPeriodIndex;
                long periodStartPositionUs = loadingPeriodHolder == null ? playbackInfo.positionUs
                        : (isFirstPeriodInWindow ? C.TIME_UNSET : 0);
                if (periodStartPositionUs == C.TIME_UNSET) {
                    Pair<Integer, Long> defaultPosition = getDefaultPosition(newLoadingPeriodIndex);
                    newLoadingPeriodIndex = defaultPosition.first;
                    periodStartPositionUs = defaultPosition.second;
                }
                Object newPeriodUid = timeline.getPeriod(newLoadingPeriodIndex, period, true).uid;
                MediaPeriod newMediaPeriod = mediaSource.createPeriod(newLoadingPeriodIndex,
                        loadControl.getAllocator(), periodStartPositionUs);
                newMediaPeriod.prepare(this);
                MediaPeriodHolder<T> newPeriodHolder = new MediaPeriodHolder<>(renderers,
                        rendererCapabilities, trackSelector, mediaSource, newMediaPeriod, newPeriodUid,
                        periodStartPositionUs);
                timeline.getWindow(windowIndex, window);
                newPeriodHolder.setIndex(timeline, window, newLoadingPeriodIndex);
                if (loadingPeriodHolder != null) {
                    loadingPeriodHolder.setNext(newPeriodHolder);
                    newPeriodHolder.rendererPositionOffsetUs = loadingPeriodHolder.rendererPositionOffsetUs
                            + timeline.getPeriod(loadingPeriodHolder.index, period).getDurationUs();
                }
                bufferAheadPeriodCount++;
                loadingPeriodHolder = newPeriodHolder;
                setIsLoading(true);
            }
        }

        if (loadingPeriodHolder == null || loadingPeriodHolder.isFullyBuffered()) {
            setIsLoading(false);
        } else if (loadingPeriodHolder != null && loadingPeriodHolder.needsContinueLoading) {
            maybeContinueLoading();
        }

        if (playingPeriodHolder == null) {
            return;
        }

        while (playingPeriodHolder != readingPeriodHolder && playingPeriodHolder.next != null
                && rendererPositionUs >= playingPeriodHolder.next.rendererPositionOffsetUs) {
            playingPeriodHolder.release();
            setPlayingPeriodHolder(playingPeriodHolder.next);
            bufferAheadPeriodCount--;
            playbackInfo = new PlaybackInfo(playingPeriodHolder.index,
                    playingPeriodHolder.startPositionUs);
            updatePlaybackPositions();
            eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
        }

        updateTimelineState();
        if (readingPeriodHolder.isLast) {
            for (Renderer renderer : enabledRenderers) {
                renderer.setCurrentStreamIsFinal();
            }
            return;
        }

        for (Renderer renderer : enabledRenderers) {
            if (!renderer.hasReadStreamToEnd()) {
                return;
            }
        }

        if (readingPeriodHolder.next != null && readingPeriodHolder.next.prepared) {
            TrackSelections<T> oldTrackSelections = readingPeriodHolder.trackSelections;
            readingPeriodHolder = readingPeriodHolder.next;
            TrackSelections<T> newTrackSelections = readingPeriodHolder.trackSelections;
            for (int i = 0; i < renderers.length; i++) {
                Renderer renderer = renderers[i];
                TrackSelection oldSelection = oldTrackSelections.get(i);
                TrackSelection newSelection = newTrackSelections.get(i);
                if (oldSelection != null) {
                    if (newSelection != null) {
                        Format[] formats = new Format[newSelection.length()];
                        for (int j = 0; j < formats.length; j++) {
                            formats[j] = newSelection.getFormat(j);
                        }
                        renderer.replaceStream(formats, readingPeriodHolder.sampleStreams[i],
                                readingPeriodHolder.rendererPositionOffsetUs);
                    } else {
                        renderer.setCurrentStreamIsFinal();
                    }
                }
            }
        }
    }

    private void maybeThrowPeriodPrepareError() throws IOException {
        if (loadingPeriodHolder != null && !loadingPeriodHolder.prepared
                && (readingPeriodHolder == null || readingPeriodHolder.next == loadingPeriodHolder)) {
            for (Renderer renderer : enabledRenderers) {
                if (!renderer.hasReadStreamToEnd()) {
                    return;
                }
            }
            loadingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
        }
    }

    private boolean haveSufficientBuffer(boolean rebuffering) {
        if (loadingPeriodHolder == null) {
            return false;
        }
        long loadingPeriodPositionUs = rendererPositionUs
                - loadingPeriodHolder.rendererPositionOffsetUs;
        long loadingPeriodBufferedPositionUs =
                !loadingPeriodHolder.prepared ? 0 : loadingPeriodHolder.mediaPeriod.getBufferedPositionUs();
        if (loadingPeriodBufferedPositionUs == C.TIME_END_OF_SOURCE) {
            if (loadingPeriodHolder.isLast) {
                return true;
            }
            loadingPeriodBufferedPositionUs = timeline.getPeriod(loadingPeriodHolder.index, period)
                    .getDurationUs();
        }
        return loadControl.shouldStartPlayback(
                loadingPeriodBufferedPositionUs - loadingPeriodPositionUs, rebuffering);
    }

    private Pair<Integer, Long> getDefaultPosition(int periodIndex) {
        timeline.getPeriod(periodIndex, period);
        timeline.getWindow(period.windowIndex, window);
        periodIndex = window.firstPeriodIndex;
        long periodPositionUs = window.getPositionInFirstPeriodUs()
                + window.getDefaultPositionMs();
        timeline.getPeriod(periodIndex, period);
        while (periodIndex < window.lastPeriodIndex
                && periodPositionUs > period.getDurationMs()) {
            periodPositionUs -= period.getDurationUs();
            timeline.getPeriod(periodIndex++, period);
        }
        return Pair.create(periodIndex, periodPositionUs);
    }

    private void setPlayingPeriodHolder(MediaPeriodHolder<T> periodHolder)
            throws ExoPlaybackException {
        int enabledRendererCount = 0;
        boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
        for (int i = 0; i < renderers.length; i++) {
            Renderer renderer = renderers[i];
            rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
            TrackSelection newSelection = periodHolder.trackSelections.get(i);
            if (newSelection != null) {
                enabledRendererCount++;
            } else if (rendererWasEnabledFlags[i]) {
                if (renderer == rendererMediaClockSource) {
                    standaloneMediaClock.setPositionUs(rendererMediaClock.getPositionUs());
                    rendererMediaClock = null;
                    rendererMediaClockSource = null;
                }
                ensureStopped(renderer);
                renderer.disable();
            }
        }
        trackSelector.onSelectionActivated(periodHolder.trackSelections);
        playingPeriodHolder = periodHolder;
        enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    }

    private void updateTimelineState() {
        long playingPeriodDurationUs = timeline.getPeriod(playingPeriodHolder.index, period)
                .getDurationUs();
        isTimelineReady = playingPeriodDurationUs == C.TIME_UNSET
                || playbackInfo.positionUs < playingPeriodDurationUs
                || (playingPeriodHolder.next != null && playingPeriodHolder.next.prepared);
        isTimelineEnded = playingPeriodHolder.isLast;
    }

    private void maybeContinueLoading() {
        long nextLoadPositionUs = loadingPeriodHolder.mediaPeriod.getNextLoadPositionUs();
        if (nextLoadPositionUs != C.TIME_END_OF_SOURCE) {
            long loadingPeriodPositionUs = rendererPositionUs
                    - loadingPeriodHolder.rendererPositionOffsetUs;
            long bufferedDurationUs = nextLoadPositionUs - loadingPeriodPositionUs;
            boolean continueLoading = loadControl.shouldContinueLoading(bufferedDurationUs);
            setIsLoading(continueLoading);
            if (continueLoading) {
                loadingPeriodHolder.needsContinueLoading = false;
                loadingPeriodHolder.mediaPeriod.continueLoading(loadingPeriodPositionUs);
            } else {
                loadingPeriodHolder.needsContinueLoading = true;
            }
        } else {
            setIsLoading(false);
        }
    }

    private void releasePeriodHoldersFrom(MediaPeriodHolder<T> periodHolder) {
        while (periodHolder != null) {
            periodHolder.release();
            periodHolder = periodHolder.next;
        }
    }

    private void enableRenderers(boolean[] renderWasEnabledFlags, int enabledRendererCount)
            throws ExoPlaybackException {
        enabledRenderers = new Renderer[enabledRendererCount];
        enabledRendererCount = 0;
        for (int i = 0; i < renderers.length; i++) {
            Renderer renderer = renderers[i];
            TrackSelection newSelection = playingPeriodHolder.trackSelections.get(i);
            if (newSelection != null) {
                enabledRenderers[enabledRendererCount++] = renderer;
                if (renderer.getState() == Renderer.STATE_DISABLED) {
                    boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
                    boolean joining = !renderWasEnabledFlags[i] && playing;
                    Format[] formats = new Format[newSelection.length()];
                    for (int j = 0; j < formats.length; j++) {
                        formats[j] = newSelection.getFormat(j);
                    }

                    renderer.enable(formats, playingPeriodHolder.sampleStreams[i], rendererPositionUs,
                            joining, playingPeriodHolder.rendererPositionOffsetUs);
                    MediaClock mediaClock = renderer.getMediaClock();
                    if (mediaClock != null) {
                        if (rendererMediaClock != null) {
                            throw ExoPlaybackException.createForUnexpected(
                                    new IllegalStateException("Multiple renderer media clocks enabled."));
                        }
                        rendererMediaClock = mediaClock;
                        rendererMediaClockSource = renderer;
                    }
                    if (playing) {
                        renderer.start();
                    }
                }
            }

        }
    }

    private static final class MediaPeriodHolder<T> {
        public final MediaPeriod mediaPeriod;
        public final Object uid;

        public final SampleStream[] sampleStreams;
        public final boolean[] mayRetainStreamFlags;

        public int index;
        public long startPositionUs;
        public boolean isLast;
        public boolean prepared;
        public boolean hasEnabledTracks;
        public long rendererPositionOffsetUs;
        public MediaPeriodHolder<T> next;
        public boolean needsContinueLoading;

        private final Renderer[] renderers;
        private final RendererCapabilities[] rendererCapabilities;
        private final TrackSelector<T> trackSelector;
        private final MediaSource mediaSource;

        private TrackSelections<T> trackSelections;
        private TrackSelections<T> periodTrackSelections;

        public MediaPeriodHolder(Renderer[] renderers, RendererCapabilities[] rendererCapabilities,
                                 TrackSelector<T> trackSelector, MediaSource mediaSource, MediaPeriod mediaPeriod,
                                 Object uid, long positionUs) {
            this.renderers = renderers;
            this.rendererCapabilities = rendererCapabilities;
            this.trackSelector = trackSelector;
            this.mediaSource = mediaSource;
            this.mediaPeriod = mediaPeriod;
            this.uid = Assertions.checkNotNull(uid);
            sampleStreams = new SampleStream[renderers.length];
            mayRetainStreamFlags = new boolean[renderers.length];
            startPositionUs = positionUs;
        }

        public void setNext(MediaPeriodHolder<T> next) {
            this.next = next;
        }

        public long updatePeriodTrackSelection(long positionUs, LoadControl loadControl,
                                               boolean forceRecreateStreams) throws ExoPlaybackException {
            return updatePeriodTrackSelection(positionUs, loadControl, forceRecreateStreams,
                    new boolean[renderers.length]);
        }

        public long updatePeriodTrackSelection(long positionUs, LoadControl loadControl,
                                               boolean forceRecreateStreams, boolean[] streamResetFlags) throws ExoPlaybackException {
            for (int i = 0; i < trackSelections.length; i++) {
                mayRetainStreamFlags[i] = !forceRecreateStreams
                        && Util.areEqual(periodTrackSelections == null ? null : periodTrackSelections.get(i),
                        trackSelections.get(i));
            }

            positionUs = mediaPeriod.selectTracks(trackSelections.getAll(), mayRetainStreamFlags,
                    sampleStreams, streamResetFlags, positionUs);
            periodTrackSelections = trackSelections;

            hasEnabledTracks = false;
            for (int i = 0; i < sampleStreams.length; i++) {
                if (sampleStreams[i] != null) {
                    Assertions.checkState(trackSelections.get(i) != null);
                    hasEnabledTracks = true;
                } else {
                    Assertions.checkState(trackSelections.get(i) == null);
                }
            }
            loadControl.onTracksSelected(renderers, mediaPeriod.getTrackGroups(), trackSelections);
            return positionUs;
        }

        public void setIndex(Timeline timeline, Timeline.Window window, int periodIndex) {
            this.index = periodIndex;
            isLast = index == timeline.getPeriodCount() - 1 && !window.isDynamic;
        }

        public boolean isFullyBuffered() {
            return prepared
                    && (!hasEnabledTracks || mediaPeriod.getBufferedPositionUs() == C.TIME_END_OF_SOURCE);
        }

        public boolean selectTracks() throws ExoPlaybackException {
            TrackSelections<T> newTrackSelections = trackSelector.selectTracks(rendererCapabilities,
                    mediaPeriod.getTrackGroups());
            if (newTrackSelections.equals(periodTrackSelections)) {
                return false;
            }
            trackSelections = newTrackSelections;
            return true;
        }

        public void handlePrepared(long positionUs, LoadControl loadControl) throws ExoPlaybackException {
            prepared = true;
            selectTracks();
            startPositionUs = updatePeriodTrackSelection(positionUs, loadControl, false);
        }

        public void release() {
            try {
                mediaSource.releasePeriod(mediaPeriod);
            } catch (RuntimeException e) {
                Log.e(TAG, "Period release failed.", e);
            }
        }
    }
}
