package com.yalin.exoplayer.source;

import android.net.Uri;
import android.os.Handler;

import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.extractor.DefaultTrackOutput;
import com.yalin.exoplayer.extractor.DefaultTrackOutput.UpstreamFormatChangedListener;
import com.yalin.exoplayer.extractor.Extractor;
import com.yalin.exoplayer.extractor.ExtractorOutput;
import com.yalin.exoplayer.extractor.SeekMap;
import com.yalin.exoplayer.extractor.TrackOutput;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.upstream.Allocator;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.Loader;
import com.yalin.exoplayer.upstream.Loader.Loadable;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

final class ExtractorMediaPeriod implements MediaPeriod, ExtractorOutput,
        Loader.Callback<ExtractorMediaPeriod.ExtractingLoadable>, UpstreamFormatChangedListener {


    public ExtractorMediaPeriod(Uri uri, DataSource dataSource, Extractor[] extractors,
                                int minLoadableRetryCount, Handler eventHandler,
                                ExtractorMediaSource.EventListener eventListener, MediaSource.Listener sourceListener,
                                Allocator allocator) {

    }

    public void release() {

    }

    @Override
    public void onUpstreamFormatChanged(Format format) {

    }

    @Override
    public TrackOutput track(int trackId) {
        return null;
    }

    @Override
    public void endTracks() {

    }

    @Override
    public void seekMap(SeekMap seekMap) {

    }

    @Override
    public void prepare(Callback callback) {

    }

    @Override
    public void maybeThrowPrepareError() throws IOException {

    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return null;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        return 0;
    }

    @Override
    public long readDiscontinuity() {
        return 0;
    }

    @Override
    public long getBufferedPositionUs() {
        return 0;
    }

    @Override
    public long seekToUs(long positionUs) {
        return 0;
    }

    @Override
    public long getNextLoadPositionUs() {
        return 0;
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return false;
    }

    @Override
    public void onLoadCompleted(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {

    }

    @Override
    public void onLoadCanceled(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {

    }

    @Override
    public int onLoadError(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error) {
        return 0;
    }

    final class ExtractingLoadable implements Loadable {

        @Override
        public void cancelLoad() {

        }

        @Override
        public boolean isLoadCanceled() {
            return false;
        }

        @Override
        public void load() throws IOException, InterruptedException {

        }
    }
}
