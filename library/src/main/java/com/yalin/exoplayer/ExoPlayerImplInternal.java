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
import com.yalin.exoplayer.trackslection.TrackSelections;
import com.yalin.exoplayer.trackslection.TrackSelector;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.MediaClock;
import com.yalin.exoplayer.util.PriorityHandlerThread;
import com.yalin.exoplayer.util.StandaloneMediaClock;
import com.yalin.exoplayer.util.TraceUtil;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

final class ExoPlayerImplInternal<T> implements Handler.Callback, TrackSelector.InvalidationListener, MediaSource.Listener {

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
        TraceUtil.endSecion();
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

    private long seekToPeriodPosition(int periodIndex, long periodPositionUs) {

    }

    private void stopInternal() {

    }

    private void releaseInternal() {

    }

    private void handlePeriodPrepared(MediaPeriod period) throws ExoPlaybackException {

    }

    private void handleSourceInfoRefreshed(Pair<Timeline, Object> timelineAndManifest)
            throws ExoPlaybackException, IOException {

    }

    private void handleContinueLoadingRequested(MediaPeriod period) {

    }

    private void reselectTracksInternal() throws ExoPlaybackException {

    }

    private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {

    }

    private void resetInternal() {

    }

    @Override
    public void onTrackSelectionsInvalidated() {

    }

    @Override
    public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {

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

    private void updatePeriods() throws ExoPlaybackException {

    }

    private void maybeThrowPeriodPrepareError() throws IOException {

    }

    private boolean haveSufficientBuffer(boolean rebuffering) {

    }

    private Pair<Integer, Long> getDefaultPosition(int periodIndex) {

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

        private TrackSelections<T> tTrackSelections;
        private TrackSelections<T> periodTrackSelections;

        public MediaPeriodHolder(Renderer[] renderers, RendererCapabilities[] rendererCapabilities,
                                 TrackSelector<T> trackSelector, MediaSource mediaSource, MediaPeriod mediaPeriod,
                                 Object uid, long positonUs) {
            this.renderers = renderers;
            this.rendererCapabilities = rendererCapabilities;
            this.trackSelector = trackSelector;
            this.mediaSource = mediaSource;
            this.mediaPeriod = mediaPeriod;
            this.uid = Assertions.checkNotNull(uid);
            sampleStreams = new SampleStream[renderers.length];
            mayRetainStreamFlags = new boolean[renderers.length];
            startPositionUs = positonUs;
        }
    }
}
