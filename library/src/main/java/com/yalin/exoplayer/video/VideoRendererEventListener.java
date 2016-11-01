package com.yalin.exoplayer.video;

import android.os.Handler;
import android.view.Surface;

import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.decoder.DecoderCounters;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public interface VideoRendererEventListener {
    void onVideoEnabled(DecoderCounters counters);

    void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
                                   long initializationDurationMs);

    void onVideoInputFormatChanged(Format format);

    void onDroppedFrames(int count, long elapsedMs);

    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                            float pixelWidthHeightRatio);

    void onRenderedFirstFrame(Surface surface);

    void onVideoDisabled(DecoderCounters counters);

    final class EventDispatcher {
        private final Handler handler;
        private final VideoRendererEventListener listener;

        public EventDispatcher(Handler handler, VideoRendererEventListener listener) {
            this.handler = handler;
            this.listener = listener;
        }

        public void enabled(final DecoderCounters decoderCounters) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onVideoEnabled(decoderCounters);
                    }
                });
            }
        }

        public void decoderInitialized(final String decoderName,
                                       final long initializedTimestampMs, final long initializationDurationMs) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onVideoDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
                    }
                });
            }
        }

        public void renderedFirstFrame(final Surface surface) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onRenderedFirstFrame(surface);
                    }
                });
            }
        }

        public void inputFormatChange(final Format format) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onVideoInputFormatChanged(format);
                    }
                });
            }
        }

        public void droppedFrames(final int droppedFrameCount, final long elapsedMs) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onDroppedFrames(droppedFrameCount, elapsedMs);
                    }
                });
            }
        }

        public void videoSizeChanged(final int width, final int height,
                                     final int unappliedRotationDegrees, final float pixelWidthHeightRatio) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
                    }
                });
            }
        }

        public void disabled(final DecoderCounters decoderCounters) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        decoderCounters.ensureUpdated();
                        listener.onVideoDisabled(decoderCounters);
                    }
                });
            }
        }
    }
}