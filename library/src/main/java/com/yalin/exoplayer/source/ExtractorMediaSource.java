package com.yalin.exoplayer.source;

import android.net.Uri;
import android.os.Handler;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.ParserException;
import com.yalin.exoplayer.Timeline;
import com.yalin.exoplayer.extractor.Extractor;
import com.yalin.exoplayer.extractor.ExtractorsFactory;
import com.yalin.exoplayer.upstream.Allocator;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class ExtractorMediaSource implements MediaSource, MediaSource.Listener {

    public interface EventListener {
        void onLoadError(IOException error);
    }

    public static final class UnrecognizedInputFormatException extends ParserException {
        public UnrecognizedInputFormatException(Extractor[] extractors) {
            super("None of this available extractors ("
                    + Util.getCommaDelimitedSimpleClassName(extractors) + ") could read the stream.");
        }
    }

    public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND = 3;

    public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_LEVE = 6;

    public static final int MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA = -1;

    private final Uri uri;
    private final DataSource.Factory dataSourceFactory;
    private final ExtractorsFactory extractorsFactory;
    private final int minLoadableRetryCount;
    private final Handler eventHandler;
    private final EventListener eventListener;
    private final Timeline.Period period;

    private MediaSource.Listener sourceListener;
    private Timeline timeline;
    private boolean timelineHasDuration;

    public ExtractorMediaSource(Uri uri, DataSource.Factory dataSourceFactory,
                                ExtractorsFactory extractorsFactory, Handler eventHandler, EventListener eventListener) {
        this(uri, dataSourceFactory, extractorsFactory, MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA, eventHandler,
                eventListener);
    }

    public ExtractorMediaSource(Uri uri, DataSource.Factory dataSourceFactory,
                                ExtractorsFactory extractorsFactory, int minLoadableRetryCount, Handler eventHandler,
                                EventListener eventListener) {
        this.uri = uri;
        this.dataSourceFactory = dataSourceFactory;
        this.extractorsFactory = extractorsFactory;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.eventHandler = eventHandler;
        this.eventListener = eventListener;
        period = new Timeline.Period();
    }

    @Override
    public void prepareSource(Listener listener) {
        sourceListener = listener;
        timeline = new SinglePeriodTimeline(C.TIME_UNSET, false);
        listener.onSourceInfoRefreshed(timeline, null);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        // Do nothing.
    }

    @Override
    public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
        Assertions.checkArgument(index == 0);
        return new ExtractorMediaPeriod(uri, dataSourceFactory.createDataSource(),
                extractorsFactory.createExtractors(), minLoadableRetryCount, eventHandler, eventListener,
                this, allocator);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((ExtractorMediaPeriod) mediaPeriod).release();
    }

    @Override
    public void releaseSource() {
        sourceListener = null;
    }

    @Override
    public void onSourceInfoRefreshed(Timeline newTimeline, Object manifest) {
        long newTimelineDurationUs = newTimeline.getPeriod(0, period).getDurationUs();
        boolean newTimelineHasDuration = newTimelineDurationUs != C.TIME_UNSET;
        if (timelineHasDuration && !newTimelineHasDuration) {
            return;
        }
        timeline = newTimeline;
        timelineHasDuration = newTimelineHasDuration;
        sourceListener.onSourceInfoRefreshed(timeline, null);
    }
}
