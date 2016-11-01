package com.yalin.exoplayer.video;

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

    void onViderInputFormatChanged(Format format);

    void onDroppedFrames(int count, long elapsedMs);

    void onViderSizeChanged(int width, int height, int unappliedRotationDegrees,
                            float pixelWidthHeightRatio);

    void onRenderedFirstFrame(Surface surface);

    void onVideoDisabled(DecoderCounters counters);

    final class EventDispatcher{

    }
}