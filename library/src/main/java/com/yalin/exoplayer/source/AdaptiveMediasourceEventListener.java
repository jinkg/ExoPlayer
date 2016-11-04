package com.yalin.exoplayer.source;

import android.os.Handler;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.upstream.DataSpec;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public interface AdaptiveMediaSourceEventListener {
    void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                       int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                       long mediaEndTimeMs, long elapsedRealtimeMs);

    void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                         int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                         long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded);

    void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                        int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                        long mediaEndTimeMs, long elapsedRealtimeMs, long loadDuarationMs, long bytesLoaded);

    void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                     int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                     long mediaEndTimeMs, long elapsedRealtimeMs, long loadDuarationMs, long bytesLoaded,
                     IOException error, boolean wasCanceled);

    void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs);

    void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason,
                                   Object trackSelectionData, long mediaTimeMs);

    final class EventDispatcher {
        private final Handler handler;
        private final AdaptiveMediaSourceEventListener listener;

        public EventDispatcher(Handler handler, AdaptiveMediaSourceEventListener listener) {
            this.handler = handler;
            this.listener = listener;
        }

        public void loadStarted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs) {
            loadStarted(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
                    null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs);
        }

        public void loadStarted(final DataSpec dataSpec, final int dataType, final int trackType,
                                final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
                                final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLoadStarted(dataSpec, dataType, trackType, trackFormat, trackSelectionReason,
                                trackSelectionData, C.usToMs(mediaStartTimeUs), C.usToMs(mediaEndTimeUs),
                                elapsedRealtimeMs);
                    }
                });
            }
        }

        public void loadCompleted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs,
                                  long loadDurationMs, long bytesLoaded) {
            loadCompleted(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
                    null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
        }

        public void loadCompleted(final DataSpec dataSpec, final int dataType, final int trackType,
                                  final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
                                  final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs,
                                  final long loadDurationMs, final long bytesLoaded) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLoadCompleted(dataSpec, dataType, trackType, trackFormat,
                                trackSelectionReason, trackSelectionData, C.usToMs(mediaStartTimeUs),
                                C.usToMs(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs, bytesLoaded);
                    }
                });
            }
        }

        public void loadCanceled(DataSpec dataSpec, int dataType, long elapsedRealtimeMs,
                                 long loadDurationMs, long bytesLoaded) {
            loadCanceled(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
                    null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
        }

        public void loadCanceled(final DataSpec dataSpec, final int dataType, final int trackType,
                                 final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
                                 final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs,
                                 final long loadDurationMs, final long bytesLoaded) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLoadCanceled(dataSpec, dataType, trackType, trackFormat,
                                trackSelectionReason, trackSelectionData, C.usToMs(mediaStartTimeUs),
                                C.usToMs(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs, bytesLoaded);
                    }
                });
            }
        }

        public void loadError(DataSpec dataSpec, int dataType, long elapsedRealtimeMs,
                              long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled) {
            loadError(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
                    null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs, loadDurationMs, bytesLoaded,
                    error, wasCanceled);
        }

        public void loadError(final DataSpec dataSpec, final int dataType, final int trackType,
                              final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
                              final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs,
                              final long loadDurationMs, final long bytesLoaded, final IOException error,
                              final boolean wasCanceled) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLoadError(dataSpec, dataType, trackType, trackFormat, trackSelectionReason,
                                trackSelectionData, C.usToMs(mediaStartTimeUs), C.usToMs(mediaEndTimeUs),
                                elapsedRealtimeMs, loadDurationMs, bytesLoaded, error, wasCanceled);
                    }
                });
            }
        }

        public void upstreamDiscarded(final int trackType, final long mediaStartTimeUs,
                                      final long mediaEndTimeUs) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onUpstreamDiscarded(trackType, C.usToMs(mediaStartTimeUs),
                                C.usToMs(mediaEndTimeUs));
                    }
                });
            }
        }

        public void downstreamFormatChanged(final int trackType, final Format trackFormat,
                                            final int trackSelectionReason, final Object trackSelectionData,
                                            final long mediaTimeUs) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onDownstreamFormatChanged(trackType, trackFormat, trackSelectionReason,
                                trackSelectionData, C.usToMs(mediaTimeUs));
                    }
                });
            }
        }

    }

}

