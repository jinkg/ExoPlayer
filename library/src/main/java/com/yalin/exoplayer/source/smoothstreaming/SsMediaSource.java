package com.yalin.exoplayer.source.smoothstreaming;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.ParserException;
import com.yalin.exoplayer.Timeline;
import com.yalin.exoplayer.source.AdaptiveMediaSourceEventListener;
import com.yalin.exoplayer.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.yalin.exoplayer.source.MediaPeriod;
import com.yalin.exoplayer.source.MediaSource;
import com.yalin.exoplayer.source.SinglePeriodTimeline;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifestParser;
import com.yalin.exoplayer.upstream.Allocator;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.Loader;
import com.yalin.exoplayer.upstream.ParsingLoadable;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class SsMediaSource implements MediaSource,
        Loader.Callback<ParsingLoadable<SsManifest>> {

    public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

    public static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30000;

    private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;

    private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5000000;

    private final Uri manifestUri;
    private final DataSource.Factory dataSourceFactory;
    private final SsChunkSource.Factory chunkSourceFactory;
    private final int minLoadableRetryCount;
    private final long livePresentationDelayMs;
    private final EventDispatcher eventDispatcher;
    private final SsManifestParser manifestParser;
    private final ArrayList<SsMediaPeriod> mediaPeriods;

    private MediaSource.Listener sourceListener;
    private DataSource manifestDataSource;
    private Loader manifestLoader;

    private long manifestLoadStartTimestamp;
    private SsManifest manifest;

    private Handler manifestRefreshHandler;

    public SsMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
                         SsChunkSource.Factory chunkSourceFactory, Handler eventHandler,
                         AdaptiveMediaSourceEventListener eventListener) {
        this(manifestUri, manifestDataSourceFactory, chunkSourceFactory,
                DEFAULT_MIN_LOADABLE_RETRY_COUNT, DEFAULT_LIVE_PRESENTATION_DELAY_MS, eventHandler,
                eventListener);
    }

    public SsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory,
                         SsChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
                         long livePresentationDelayMs, Handler eventHandler,
                         AdaptiveMediaSourceEventListener eventListener) {
        this.manifestUri = Util.toLowerInvariant(manifestUri.getLastPathSegment()).equals("manifest")
                ? manifestUri : Uri.withAppendedPath(manifestUri, "Manifest");
        this.dataSourceFactory = dataSourceFactory;
        this.chunkSourceFactory = chunkSourceFactory;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.livePresentationDelayMs = livePresentationDelayMs;
        this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
        manifestParser = new SsManifestParser();
        mediaPeriods = new ArrayList<>();
    }

    @Override
    public void prepareSource(Listener listener) {
        sourceListener = listener;
        manifestDataSource = dataSourceFactory.creteDataSource();
        manifestLoader = new Loader("Loader:Manifest");
        manifestRefreshHandler = new Handler();
        startLoadingManifest();
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        manifestLoader.maybeThrowError();
    }

    @Override
    public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
        Assertions.checkArgument(index == 0);
        SsMediaPeriod period = new SsMediaPeriod(manifest, chunkSourceFactory, minLoadableRetryCount,
                eventDispatcher, manifestLoader, allocator);
        mediaPeriods.add(period);
        return period;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((SsMediaPeriod) mediaPeriod).release();
        mediaPeriods.remove(mediaPeriod);
    }

    @Override
    public void releaseSource() {
        sourceListener = null;
        manifest = null;
        manifestDataSource = null;
        manifestLoadStartTimestamp = 0;
        if (manifestLoader != null) {
            manifestLoader.release();
            manifestLoader = null;
        }
        if (manifestRefreshHandler != null) {
            manifestRefreshHandler.removeCallbacksAndMessages(null);
            manifestRefreshHandler = null;
        }
    }

    @Override
    public void onLoadCompleted(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
                                long loadDurationMs) {
        eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
                loadDurationMs, loadable.bytesLoaded());
        manifest = loadable.getResult();
        manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
        for (int i = 0; i < mediaPeriods.size(); i++) {
            mediaPeriods.get(i).updateManifest(manifest);
        }
        Timeline timeline;
        if (manifest.isLive) {
            long startTimeUs = Long.MAX_VALUE;
            long endTimeUs = Long.MIN_VALUE;
            for (int i = 0; i < manifest.streamElements.length; i++) {
                StreamElement element = manifest.streamElements[i];
                if (element.chunkCount > 0) {
                    startTimeUs = Math.min(startTimeUs, element.getStartTimeUs(0));
                    endTimeUs = Math.max(endTimeUs, element.getStartTimeUs(element.chunkCount - 1)
                            + element.getChunkDurationUs(element.chunkCount - 1));
                }
            }
            if (startTimeUs == Long.MAX_VALUE) {
                timeline = new SinglePeriodTimeline(C.TIME_UNSET, false);
            } else {
                if (manifest.dvrWindowLengthUs != C.TIME_UNSET
                        && manifest.dvrWindowLengthUs > 0) {
                    startTimeUs = Math.max(startTimeUs, endTimeUs - manifest.dvrWindowLengthUs);
                }
                long durationUs = endTimeUs - startTimeUs;
                long defaultStartPositionUs = durationUs - C.msToUs(livePresentationDelayMs);
                if (defaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
                    defaultStartPositionUs = Math.min(MIN_LIVE_DEFAULT_START_POSITION_US, durationUs / 2);
                }
                timeline = new SinglePeriodTimeline(C.TIME_UNSET, durationUs, startTimeUs,
                        defaultStartPositionUs, true, true);
            }
        } else {
            boolean isSeekable = manifest.durationUs != C.TIME_UNSET;
            timeline = new SinglePeriodTimeline(manifest.durationUs, isSeekable);
        }
        sourceListener.onSourceInfoRefreshed(timeline, manifest);
        scheduleManifestRefresh();
    }

    @Override
    public void onLoadCanceled(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
                               long loadDurationMs, boolean released) {
        eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
                loadDurationMs, loadable.bytesLoaded());
    }

    @Override
    public int onLoadError(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
                           long loadDurationMs, IOException error) {
        boolean isFatal = error instanceof ParserException;
        eventDispatcher.loadError(loadable.dataSpec, loadable.type, elapsedRealtimeMs, loadDurationMs,
                loadable.bytesLoaded(), error, isFatal);
        return isFatal ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
    }

    private void scheduleManifestRefresh() {
        if (!manifest.isLive) {
            return;
        }
        long nextLoadTimestamp = manifestLoadStartTimestamp + MINIMUM_MANIFEST_REFRESH_PERIOD_MS;
        long delayUntilNexLoad = Math.max(0, nextLoadTimestamp - SystemClock.elapsedRealtime());
        manifestRefreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startLoadingManifest();
            }
        }, delayUntilNexLoad);
    }

    private void startLoadingManifest() {
        ParsingLoadable<SsManifest> loadable = new ParsingLoadable<>(manifestDataSource,
                manifestUri, C.DATA_TYPE_MANIFEST, manifestParser);
        long elapsedRealtimeMs = manifestLoader.startLoading(loadable, this, minLoadableRetryCount);
        eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
    }
}
