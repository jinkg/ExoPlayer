package com.yalin.exoplayer.source;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseArray;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.FormatHolder;
import com.yalin.exoplayer.decoder.DecoderInputBuffer;
import com.yalin.exoplayer.extractor.DefaultExtractorInput;
import com.yalin.exoplayer.extractor.DefaultTrackOutput;
import com.yalin.exoplayer.extractor.DefaultTrackOutput.UpstreamFormatChangedListener;
import com.yalin.exoplayer.extractor.Extractor;
import com.yalin.exoplayer.extractor.ExtractorInput;
import com.yalin.exoplayer.extractor.ExtractorOutput;
import com.yalin.exoplayer.extractor.PositionHolder;
import com.yalin.exoplayer.extractor.SeekMap;
import com.yalin.exoplayer.extractor.TrackOutput;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.upstream.Allocator;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.DataSpec;
import com.yalin.exoplayer.upstream.Loader;
import com.yalin.exoplayer.upstream.Loader.Loadable;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.ConditionVariable;
import com.yalin.exoplayer.util.Util;

import java.io.EOFException;
import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

final class ExtractorMediaPeriod implements MediaPeriod, ExtractorOutput,
        Loader.Callback<ExtractorMediaPeriod.ExtractingLoadable>, UpstreamFormatChangedListener {

    private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10000;

    private final Uri uri;
    private final DataSource dataSource;
    private final int minLoadableRetryCount;
    private final Handler eventHandler;
    private final ExtractorMediaSource.EventListener eventListener;
    private final MediaSource.Listener sourceListener;
    private final Allocator allocator;
    private final Loader loader;
    private final ExtractorHolder extractorHolder;
    private final ConditionVariable loadCondition;
    private final Runnable maybeFinishPrepareRunnable;
    private final Runnable onContinueLoadingRequestedRunnable;
    private final Handler handler;
    private final SparseArray<DefaultTrackOutput> sampleQueues;

    private Callback callback;
    private SeekMap seekMap;
    private boolean tracksBuilt;
    private boolean prepared;

    private boolean seenFirstTrackSelection;
    private boolean notifyReset;
    private int enabledTrackCount;
    private TrackGroupArray tracks;
    private long durationUs;
    private boolean[] trackEnabledStates;
    private long length;

    private long lastSeekPositionUs;
    private long pendingResetPositionUs;

    private int extractedSamplesCountAtStartOfLoad;
    private boolean loadingFinished;
    private boolean released;

    public ExtractorMediaPeriod(Uri uri, DataSource dataSource, Extractor[] extractors,
                                int minLoadableRetryCount, Handler eventHandler,
                                ExtractorMediaSource.EventListener eventListener, MediaSource.Listener sourceListener,
                                Allocator allocator) {
        this.uri = uri;
        this.dataSource = dataSource;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.eventHandler = eventHandler;
        this.eventListener = eventListener;
        this.sourceListener = sourceListener;
        this.allocator = allocator;
        loader = new Loader("Loader:ExtractorMediaPeriod");
        extractorHolder = new ExtractorHolder(extractors, this);
        loadCondition = new ConditionVariable();
        maybeFinishPrepareRunnable = new Runnable() {
            @Override
            public void run() {
                maybeFinishPrepare();
            }
        };
        onContinueLoadingRequestedRunnable = new Runnable() {
            @Override
            public void run() {
                if (!released) {
                    callback.onContinueLoadingRequested(ExtractorMediaPeriod.this);
                }
            }
        };
        handler = new Handler();

        pendingResetPositionUs = C.TIME_UNSET;
        sampleQueues = new SparseArray<>();
        length = C.LENGTH_UNSET;
    }

    public void release() {
        final ExtractorHolder extractorHolder = this.extractorHolder;
        loader.release(new Runnable() {
            @Override
            public void run() {
                extractorHolder.release();
                int trackCount = sampleQueues.size();
                for (int i = 0; i < trackCount; i++) {
                    sampleQueues.valueAt(i).disable();
                }
            }
        });
        handler.removeCallbacksAndMessages(null);
        released = true;
    }

    @Override
    public void onUpstreamFormatChanged(Format format) {
        handler.post(maybeFinishPrepareRunnable);
    }

    @Override
    public TrackOutput track(int trackId) {
        DefaultTrackOutput trackOutput = sampleQueues.get(trackId);
        if (trackOutput == null) {
            trackOutput = new DefaultTrackOutput(allocator);
            trackOutput.setUpstreamFormatChangeListener(this);
            sampleQueues.put(trackId, trackOutput);
        }
        return trackOutput;
    }

    @Override
    public void endTracks() {
        tracksBuilt = true;
        handler.post(maybeFinishPrepareRunnable);
    }

    @Override
    public void seekMap(SeekMap seekMap) {
        this.seekMap = seekMap;
        handler.post(maybeFinishPrepareRunnable);
    }

    @Override
    public void prepare(Callback callback) {
        this.callback = callback;
        loadCondition.open();
        startLoading();
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
        maybeThrowError();
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return tracks;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
                             SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        Assertions.checkState(prepared);

        for (int i = 0; i < selections.length; i++) {
            if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                int track = ((SampleStreamImpl) streams[i]).track;
                Assertions.checkState(trackEnabledStates[track]);
                enabledTrackCount--;
                trackEnabledStates[track] = false;
                sampleQueues.valueAt(track).disable();
                streams[i] = null;
            }
        }
        boolean selectedNewTracks = false;
        for (int i = 0; i < selections.length; i++) {
            if (streams[i] == null && selections[i] != null) {
                TrackSelection selection = selections[i];
                Assertions.checkState(selection.length() == 1);
                Assertions.checkState(selection.getIndexInTrackGroup(0) == 0);
                int track = tracks.indexOf(selection.getTrackGroup());
                Assertions.checkState(!trackEnabledStates[track]);
                enabledTrackCount++;
                trackEnabledStates[track] = true;
                streams[i] = new SampleStreamImpl(track);
                streamResetFlags[i] = true;
                selectedNewTracks = true;
            }
        }
        if (!seenFirstTrackSelection) {
            int trackCount = sampleQueues.size();
            for (int i = 0; i < trackCount; i++) {
                if (!trackEnabledStates[i]) {
                    sampleQueues.valueAt(i).disable();
                }
            }
        }
        if (enabledTrackCount == 0) {
            notifyReset = false;
            if (loader.isLoading()) {
                loader.cancelLoading();
            }
        } else if (seenFirstTrackSelection ? selectedNewTracks : positionUs != 0) {
            positionUs = seekToUs(positionUs);
            for (int i = 0; i < streams.length; i++) {
                if (streams[i] != null) {
                    streamResetFlags[i] = true;
                }
            }
        }
        seenFirstTrackSelection = true;
        return positionUs;
    }

    @Override
    public long readDiscontinuity() {
        if (notifyReset) {
            notifyReset = false;
            return lastSeekPositionUs;
        }
        return C.TIME_UNSET;
    }

    @Override
    public long getBufferedPositionUs() {
        if (loadingFinished) {
            return C.TIME_END_OF_SOURCE;
        } else if (isPendingReset()) {
            return pendingResetPositionUs;
        } else {
            long largestQueuedTimestampUs = getLargestQueuedTimestampUs();
            return largestQueuedTimestampUs == Long.MIN_VALUE ? lastSeekPositionUs
                    : largestQueuedTimestampUs;
        }
    }

    @Override
    public long seekToUs(long positionUs) {
        positionUs = seekMap.isSeekable() ? positionUs : 0;
        lastSeekPositionUs = positionUs;
        int trackCount = sampleQueues.size();
        boolean seekInsideBuffer = !isPendingReset();
        for (int i = 0; seekInsideBuffer && i < trackCount; i++) {
            if (trackEnabledStates[i]) {
                seekInsideBuffer = sampleQueues.valueAt(i).skipToKeyframeBefore(positionUs);
            }
        }

        if (!seekInsideBuffer) {
            pendingResetPositionUs = positionUs;
            loadingFinished = false;
            if (loader.isLoading()) {
                loader.cancelLoading();
            } else {
                for (int i = 0; i < trackCount; i++) {
                    sampleQueues.valueAt(i).reset(trackEnabledStates[i]);
                }
            }
        }
        notifyReset = false;
        return positionUs;
    }

    @Override
    public long getNextLoadPositionUs() {
        return getBufferedPositionUs();
    }

    @Override
    public boolean continueLoading(long positionUs) {
        if (loadingFinished) {
            return false;
        }
        boolean continuedLoading = loadCondition.open();
        if (!loader.isLoading()) {
            startLoading();
            continuedLoading = true;
        }
        return continuedLoading;
    }

    @Override
    public void onLoadCompleted(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
        copyLengthFromLoader(loadable);
        loadingFinished = true;
        if (durationUs == C.TIME_UNSET) {
            long largestQueuedTimestampUs = getLargestQueuedTimestampUs();
            durationUs = largestQueuedTimestampUs == Long.MIN_VALUE ? 0
                    : largestQueuedTimestampUs + DEFAULT_LAST_SAMPLE_DURATION_US;
            sourceListener.onSourceInfoRefreshed(
                    new SinglePeriodTimeline(durationUs, seekMap.isSeekable()), null);

        }
    }

    @Override
    public void onLoadCanceled(ExtractingLoadable loadable, long elapsedRealtimeMs,
                               long loadDurationMs, boolean released) {
        copyLengthFromLoader(loadable);
        if (!released && enabledTrackCount > 0) {
            int trackCount = sampleQueues.size();
            for (int i = 0; i < trackCount; i++) {
                sampleQueues.valueAt(i).reset(trackEnabledStates[i]);
            }
            callback.onContinueLoadingRequested(this);
        }
    }

    @Override
    public int onLoadError(ExtractingLoadable loadable, long elapsedRealtimeMs,
                           long loadDurationMs, IOException error) {
        copyLengthFromLoader(loadable);
        notifyLoadError(error);
        if (isLoadableExceptionFatal(error)) {
            return Loader.DONT_RETRY_FATAL;
        }
        int extractedSamplesCount = getExtractedSamplesCount();
        boolean madeProgress = extractedSamplesCount > extractedSamplesCountAtStartOfLoad;
        configureRetry(loadable);
        extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();
        return madeProgress ? Loader.RETRY_RESET_ERROR_COUNT : Loader.RETRY;
    }

    boolean isReady(int track) {
        return loadingFinished || (!isPendingReset() && !sampleQueues.valueAt(track).isEmpty());
    }

    void maybeThrowError() throws IOException {
        loader.maybeThrowError();
    }

    int readData(int track, FormatHolder formatHolder, DecoderInputBuffer buffer) {
        if (notifyReset || isPendingReset()) {
            return C.RESULT_NOTHING_READ;
        }
        return sampleQueues.valueAt(track).readData(formatHolder, buffer, loadingFinished,
                lastSeekPositionUs);
    }

    private void configureRetry(ExtractingLoadable loadable) {
        if (length != C.LENGTH_UNSET
                || (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET)) {

        } else {
            lastSeekPositionUs = 0;
            notifyReset = prepared;
            int trackCount = sampleQueues.size();
            for (int i = 0; i < trackCount; i++) {
                sampleQueues.valueAt(i).reset(!prepared || trackEnabledStates[i]);
            }
            loadable.setLoadPosition(0);
        }
    }

    private int getExtractedSamplesCount() {
        int extractedSamplesCount = 0;
        int trackCount = sampleQueues.size();
        for (int i = 0; i < trackCount; i++) {
            extractedSamplesCount += sampleQueues.valueAt(i).getWriteIndex();
        }
        return extractedSamplesCount;
    }

    private long getLargestQueuedTimestampUs() {
        long largestQueuedTimestampUs = Long.MIN_VALUE;
        int trackCount = sampleQueues.size();
        for (int i = 0; i < trackCount; i++) {
            largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs,
                    sampleQueues.valueAt(i).getLargestQueuedTimestampUs());
        }
        return largestQueuedTimestampUs;
    }

    private boolean isPendingReset() {
        return pendingResetPositionUs != C.TIME_UNSET;
    }

    private void startLoading() {
        ExtractingLoadable loadable = new ExtractingLoadable(uri, dataSource, extractorHolder,
                loadCondition);
        if (prepared) {
            Assertions.checkState(isPendingReset());
            if (durationUs != C.TIME_UNSET && pendingResetPositionUs >= durationUs) {
                loadingFinished = true;
                pendingResetPositionUs = C.TIME_UNSET;
                return;
            }
            loadable.setLoadPosition(seekMap.getPosition(pendingResetPositionUs));
            pendingResetPositionUs = C.TIME_UNSET;
        }
        extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();

        int minRetryCount = minLoadableRetryCount;
        if (minRetryCount == ExtractorMediaSource.MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA) {
            minRetryCount = !prepared || length != C.LENGTH_UNSET
                    || (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET)
                    ? ExtractorMediaSource.DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND
                    : ExtractorMediaSource.DEFAULT_MIN_LOADABLE_RETRY_COUNT_LEVE;
        }
        loader.startLoading(loadable, this, minRetryCount);
    }

    private void maybeFinishPrepare() {
        if (released || prepared || seekMap == null || !tracksBuilt) {
            return;
        }
        int trackCount = sampleQueues.size();
        for (int i = 0; i < trackCount; i++) {
            if (sampleQueues.valueAt(i).getUpstreamFormat() == null) {
                return;
            }
        }
        loadCondition.close();
        TrackGroup[] trackArray = new TrackGroup[trackCount];
        trackEnabledStates = new boolean[trackCount];
        durationUs = seekMap.getDurationUs();
        for (int i = 0; i < trackCount; i++) {
            trackArray[i] = new TrackGroup(sampleQueues.valueAt(i).getUpstreamFormat());
        }
        tracks = new TrackGroupArray(trackArray);
        prepared = true;
        sourceListener.onSourceInfoRefreshed(
                new SinglePeriodTimeline(durationUs, seekMap.isSeekable()), null);
        callback.onPrepared(this);
    }

    private void copyLengthFromLoader(ExtractingLoadable loadable) {
        if (length == C.LENGTH_UNSET) {
            length = loadable.length;
        }
    }

    private boolean isLoadableExceptionFatal(IOException e) {
        return e instanceof ExtractorMediaSource.UnrecognizedInputFormatException;
    }

    private void notifyLoadError(final IOException error) {
        if (eventHandler != null && eventListener != null) {
            eventHandler.post(new Runnable() {
                @Override
                public void run() {
                    eventListener.onLoadError(error);
                }
            });
        }
    }

    private final class SampleStreamImpl implements SampleStream {

        private final int track;

        public SampleStreamImpl(int track) {
            this.track = track;
        }

        @Override
        public boolean isReady() {
            return ExtractorMediaPeriod.this.isReady(track);
        }

        @Override
        public void maybeThrowError() throws IOException {
            ExtractorMediaPeriod.this.maybeThrowError();
        }

        @Override
        public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
            return ExtractorMediaPeriod.this.readData(track, formatHolder, buffer);
        }

        @Override
        public void skipToKeyframeBefore(long timeUs) {
            sampleQueues.valueAt(track).skipToKeyframeBefore(timeUs);
        }
    }

    final class ExtractingLoadable implements Loadable {

        private static final int CONTINUE_LOADING_CHECK_INTERVAL_BYTES = 1024 * 1024;

        private final Uri uri;
        private final DataSource dataSource;
        private final ExtractorHolder extractorHolder;
        private final ConditionVariable loadCondition;
        private final PositionHolder positionHolder;

        private volatile boolean loadCanceled;

        private boolean pendingExtractorSeek;
        private long length;

        public ExtractingLoadable(Uri uri, DataSource dataSource, ExtractorHolder extractorHolder,
                                  ConditionVariable loadCondition) {
            this.uri = Assertions.checkNotNull(uri);
            this.dataSource = Assertions.checkNotNull(dataSource);
            this.extractorHolder = Assertions.checkNotNull(extractorHolder);
            this.loadCondition = loadCondition;
            this.positionHolder = new PositionHolder();
            this.pendingExtractorSeek = true;
            this.length = C.LENGTH_UNSET;
        }

        public void setLoadPosition(long position) {
            positionHolder.position = position;
            pendingExtractorSeek = true;
        }

        @Override
        public void cancelLoad() {
            loadCanceled = true;
        }

        @Override
        public boolean isLoadCanceled() {
            return loadCanceled;
        }

        @Override
        public void load() throws IOException, InterruptedException {
            int result = Extractor.RESULT_CONTINUE;
            while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
                ExtractorInput input = null;
                try {
                    long position = positionHolder.position;
                    length = dataSource.open(
                            new DataSpec(uri, position, C.LENGTH_UNSET, Util.sha1(uri.toString())));
                    if (length != C.LENGTH_UNSET) {
                        length += position;
                    }
                    input = new DefaultExtractorInput(dataSource, position, length);
                    Extractor extractor = extractorHolder.selectExtractor(input);
                    if (pendingExtractorSeek) {
                        extractor.seek(position);
                        pendingExtractorSeek = false;
                    }
                    while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
                        loadCondition.block();
                        result = extractor.read(input, positionHolder);
                        if (input.getPosition() > position + CONTINUE_LOADING_CHECK_INTERVAL_BYTES) {
                            position = input.getPosition();
                            loadCondition.close();
                            handler.post(onContinueLoadingRequestedRunnable);
                        }
                    }
                } finally {
                    if (result == Extractor.RESULT_SEEK) {
                        result = Extractor.RESULT_CONTINUE;
                    } else if (input != null) {
                        positionHolder.position = input.getPosition();
                    }
                    dataSource.close();
                }
            }
        }
    }

    private static final class ExtractorHolder {
        private final Extractor[] extractors;
        private final ExtractorOutput extractorOutput;
        private Extractor extractor;

        public ExtractorHolder(Extractor[] extractors, ExtractorOutput extractorOutput) {
            this.extractors = extractors;
            this.extractorOutput = extractorOutput;
        }

        public Extractor selectExtractor(ExtractorInput input)
                throws IOException, InterruptedException {
            if (extractor != null) {
                return extractor;
            }
            for (Extractor extractor : extractors) {
                try {
                    if (extractor.sniff(input)) {
                        this.extractor = extractor;
                        break;
                    }
                } catch (EOFException e) {
                    // Do nothing.
                } finally {
                    input.resetPeekPosition();
                }
            }
            if (extractor == null) {
                throw new ExtractorMediaSource.UnrecognizedInputFormatException(extractors);
            }
            extractor.init(extractorOutput);
            return extractor;
        }

        public void release() {
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }
}
