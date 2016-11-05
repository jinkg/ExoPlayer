package com.yalin.exoplayer.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.drm.DrmInitData;
import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;
import com.yalin.exoplayer.mediacodec.MediaCodecInfo;
import com.yalin.exoplayer.mediacodec.MediaCodecRenderer;
import com.yalin.exoplayer.mediacodec.MediaCodecSelector;
import com.yalin.exoplayer.mediacodec.MediaCodecUtil;
import com.yalin.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.yalin.exoplayer.util.MimeTypes;
import com.yalin.exoplayer.util.TraceUtil;
import com.yalin.exoplayer.util.Util;
import com.yalin.exoplayer.video.VideoRendererEventListener.EventDispatcher;

import java.nio.ByteBuffer;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */
@TargetApi(16)
public class MediaCodecVideoRenderer extends MediaCodecRenderer {

    private static final String KEY_CROP_LEFT = "crop_left";
    private static final String KEY_CROP_RIGHT = "crop_right";
    private static final String KEY_CROP_BOTTOM = "crop_bottom";
    private static final String KEY_CROP_TOP = "crop_top";

    private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
    private final EventDispatcher eventDispatcher;
    private final long allowedJoiningTimeMs;
    private final int videoScalingMode;
    private final int maxDroppedFramesToNotify;
    private final boolean deviceNeedsAutoFrcWorkaround;

    private Format[] streamFormats;
    private CodecMaxValues codecMaxValues;

    private Surface surface;
    private boolean renderedFirstFrame;
    private long joiningDeadlineMs;
    private long droppedFrameAccumulationStartTimeMs;
    private int droppedFrames;
    private int consecutiveDroppedFrameCount;

    private int pendingRotationDegrees;
    private float pendingPixelWidthHeightRatio;
    private int currentWidth;
    private int currentHeight;
    private int currentUnappliedRotationDegrees;
    private float currentPixelWidthHeightRatio;
    private int lastReportedWidth;
    private int lastReportedHeight;
    private int lastReportedUnappliedRotationDegrees;
    private float lastReportedPixelWidthHeightRatio;

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                   int videoScalingMode, long allowedJoiningTimeMs) {
        this(context, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, null, -1);
    }

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                   int videoScalingMode, long allowedJoiningTimeMs, Handler eventHandler,
                                   VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        this(context, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, false,
                eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                   int videoScalingMode, long allowedJoiningTimeMs,
                                   DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                   boolean playClearSamplesWithoutKeys, Handler eventHandler,
                                   VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(C.TRACK_TYPE_VIDEO, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys);
        this.videoScalingMode = videoScalingMode;
        this.allowedJoiningTimeMs = allowedJoiningTimeMs;
        this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
        frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(context);
        eventDispatcher = new EventDispatcher(eventHandler, eventListener);
        deviceNeedsAutoFrcWorkaround = deviceNeedsAutoFrcWorkaround();
        joiningDeadlineMs = C.TIME_UNSET;
        currentWidth = Format.NO_VALUE;
        currentHeight = Format.NO_VALUE;
        currentPixelWidthHeightRatio = Format.NO_VALUE;
        pendingPixelWidthHeightRatio = Format.NO_VALUE;
        lastReportedWidth = Format.NO_VALUE;
        lastReportedHeight = Format.NO_VALUE;
        lastReportedPixelWidthHeightRatio = Format.NO_VALUE;
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec,
                                          ByteBuffer buffer, int bufferIndex, int bufferFlags,
                                          long bufferPresentationTimeUs, boolean shouldSkip) {
        if (shouldSkip) {
            skipOutputBuffer(codec, bufferIndex);
            return true;
        }

        if (!renderedFirstFrame) {
            if (Util.SDK_INT >= 21) {
                renderOutputBufferV21(codec, bufferIndex, System.nanoTime());
            } else {
                renderOutputBuffer(codec, bufferIndex);
            }
            return true;
        }
        if (getState() != STATE_STARTED) {
            return false;
        }

        long elapsedSinceStartOfLoopUs = (SystemClock.elapsedRealtime() * 1000) - elapsedRealtimeUs;
        long earlyUs = bufferPresentationTimeUs - positionUs - elapsedSinceStartOfLoopUs;

        long systemTimeNs = System.nanoTime();
        long unadjustedFrameReleaseTimeNs = systemTimeNs + (earlyUs * 1000);

        long adjustedReleaseTimeNs = frameReleaseTimeHelper.adjustReleaseTime(
                bufferPresentationTimeUs, unadjustedFrameReleaseTimeNs);
        earlyUs = (adjustedReleaseTimeNs - systemTimeNs) / 1000;

        if (earlyUs < -30000) {
            dropOutputBuffer(codec, bufferIndex);
            return true;
        }

        if (Util.SDK_INT >= 21) {
            if (earlyUs < 50000) {
                renderOutputBufferV21(codec, bufferIndex, adjustedReleaseTimeNs);
                return true;
            }
        } else {
            if (earlyUs < 30000) {
                if (earlyUs > 11000) {
                    try {
                        Thread.sleep((earlyUs - 10000) / 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                renderOutputBuffer(codec, bufferIndex);
                return true;
            }
        }
        return false;
    }

    private void skipOutputBuffer(MediaCodec codec, int bufferIndex) {
        TraceUtil.beginSection("skipVideoBuffer");
        codec.releaseOutputBuffer(bufferIndex, false);
        TraceUtil.endSection();
        decoderCounters.skippedOutputBufferCount++;
    }

    private void dropOutputBuffer(MediaCodec codec, int bufferIndex) {
        TraceUtil.beginSection("dropVideoBuffer");
        codec.releaseOutputBuffer(bufferIndex, false);
        TraceUtil.endSection();
        decoderCounters.droppedOutputBufferCount++;
        droppedFrames++;
        consecutiveDroppedFrameCount++;
        decoderCounters.maxConsecutiveDroppedOutputBufferCount = Math.max(consecutiveDroppedFrameCount,
                decoderCounters.maxConsecutiveDroppedOutputBufferCount);
        if (droppedFrames == maxDroppedFramesToNotify) {
            maybeNotifyVideoSizeChanged();
        }
    }

    @Override
    public boolean isReady() {
        if ((renderedFirstFrame || super.shouldInitCodec()) && super.isReady()) {
            joiningDeadlineMs = C.TIME_UNSET;
            return true;
        } else if (joiningDeadlineMs == C.TIME_UNSET) {
            return false;
        } else if (SystemClock.elapsedRealtime() < joiningDeadlineMs) {
            return true;
        } else {
            joiningDeadlineMs = C.TIME_UNSET;
            return false;
        }
    }

    private static boolean deviceNeedsAutoFrcWorkaround() {
        return Util.SDK_INT <= 22 && "foster".equals(Util.DEVICE) &&
                "NVIDIA".equals(Util.MANUFACTURER);
    }

    @Override
    protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
            throws DecoderQueryException {
        String mimeType = format.sampleMimeType;
        if (!MimeTypes.isVideo(mimeType)) {
            return FORMAT_UNSUPPORTED_TYPE;
        }
        boolean requiresSecureDecryption = false;
        DrmInitData drmInitData = format.drmInitData;
        if (drmInitData != null) {
            for (int i = 0; i < drmInitData.schemeDataCount; i++) {
                requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
            }
        }
        MediaCodecInfo decoderInfo = mediaCodecSelector.getDecoderInfo(mimeType,
                requiresSecureDecryption);
        if (decoderInfo == null) {
            return FORMAT_UNSUPPORTED_SUBTYPE;
        }

        boolean decoderCapable = decoderInfo.isCodecSupported(format.codecs);
        if (decoderCapable && format.width > 0 && format.height > 0) {
            if (Util.SDK_INT >= 21) {
                if (format.frameRate > 0) {
                    decoderCapable = decoderInfo.isVideoSizeAndRateSupportedV21(format.width, format.height, format.frameRate);
                } else {
                    decoderCapable = decoderInfo.isVideoSizeSupportedV21(format.width, format.height);
                }
            } else {
                decoderCapable = format.width * format.height <= MediaCodecUtil.maxH264DecodableFrameSize();
            }
        }

        int adaptiveSupport = decoderInfo.adaptive ? ADAPTIVE_SEAMLESS : ADAPTIVE_NOT_SEAMLESS;
        int formatSupport = decoderCapable ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
        return adaptiveSupport | formatSupport;
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        super.onEnabled(joining);
        eventDispatcher.enabled(decoderCounters);
        frameReleaseTimeHelper.enable();
    }

    @Override
    protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
        streamFormats = formats;
        super.onStreamChanged(formats);
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
        super.onPositionReset(positionUs, joining);
        renderedFirstFrame = false;
        consecutiveDroppedFrameCount = 0;
        joiningDeadlineMs = joining && allowedJoiningTimeMs > 0
                ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs) : C.TIME_UNSET;
    }

    @Override
    protected void onStarted() {
        super.onStarted();
        droppedFrames = 0;
        droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onStopped() {
        joiningDeadlineMs = C.TIME_UNSET;
        maybeNotifyDroppedFrames();
        super.onStopped();
    }

    @Override
    protected void onDisabled() {
        currentWidth = Format.NO_VALUE;
        currentHeight = Format.NO_VALUE;
        currentPixelWidthHeightRatio = Format.NO_VALUE;
        pendingPixelWidthHeightRatio = Format.NO_VALUE;
        lastReportedWidth = Format.NO_VALUE;
        lastReportedHeight = Format.NO_VALUE;
        lastReportedPixelWidthHeightRatio = Format.NO_VALUE;
        frameReleaseTimeHelper.disable();
        try {
            super.onDisabled();
        } finally {
            decoderCounters.ensureUpdated();
            eventDispatcher.disabled(decoderCounters);
        }
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        if (messageType == C.MSG_SET_SURFACE) {
            setSurface((Surface) message);
        } else {
            super.handleMessage(messageType, message);
        }
    }

    private void setSurface(Surface surface) throws ExoPlaybackException {
        if (this.surface == surface) {
            return;
        }
        renderedFirstFrame = false;
        this.surface = surface;
        int state = getState();
        if (state == STATE_ENABLED || state == STATE_STARTED) {
            releaseCodec();
            maybeInitCodec();
        }
    }

    @Override
    protected boolean shouldInitCodec() {
        return super.shouldInitCodec() && surface != null && surface.isValid();
    }

    @Override
    protected void configureCodec(MediaCodec codec, Format format, MediaCrypto crypto) {
        codecMaxValues = getCodecMaxValues(format, streamFormats);
        MediaFormat mediaFormat = getMediaFormat(format, codecMaxValues, deviceNeedsAutoFrcWorkaround);
        codec.configure(mediaFormat, surface, crypto, 0);
    }

    @Override
    protected void onCodecInitialized(String name, long initializedTimestampMs,
                                      long initializationDurationMs) {
        eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
        super.onInputFormatChanged(newFormat);
        eventDispatcher.inputFormatChange(newFormat);
        pendingPixelWidthHeightRatio = getPixelWidthHeightRatio(newFormat);
        pendingRotationDegrees = getRotationDegrees(newFormat);
    }

    @Override
    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {
        boolean hasCrop = outputFormat.containsKey(KEY_CROP_RIGHT)
                && outputFormat.containsKey(KEY_CROP_LEFT) && outputFormat.containsKey(KEY_CROP_BOTTOM)
                && outputFormat.containsKey(KEY_CROP_TOP);
        currentWidth = hasCrop
                ? outputFormat.getInteger(KEY_CROP_RIGHT) - outputFormat.getInteger(KEY_CROP_LEFT) + 1
                : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
        currentHeight = hasCrop
                ? outputFormat.getInteger(KEY_CROP_BOTTOM) - outputFormat.getInteger(KEY_CROP_TOP) + 1
                : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        currentPixelWidthHeightRatio = pendingPixelWidthHeightRatio;
        if (Util.SDK_INT >= 21) {
            if (pendingRotationDegrees == 90 || pendingRotationDegrees == 270) {
                int rotatedHeight = currentWidth;
                currentWidth = currentHeight;
                currentHeight = rotatedHeight;
                currentPixelWidthHeightRatio = 1 / currentPixelWidthHeightRatio;
            }
        } else {
            currentUnappliedRotationDegrees = pendingRotationDegrees;
        }
        codec.setVideoScalingMode(videoScalingMode);
    }

    @Override
    protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
                                          Format oldFormat, Format newFormat) {
        return areAdaptationCompatible(oldFormat, newFormat)
                && newFormat.width <= codecMaxValues.width && newFormat.height <= codecMaxValues.height
                && newFormat.maxInputSize <= codecMaxValues.inputSize
                && (codecIsAdaptive
                || (oldFormat.width == newFormat.width && oldFormat.height == newFormat.height));
    }

    private void renderOutputBuffer(MediaCodec codec, int bufferIndex) {
        maybeNotifyVideoSizeChanged();
        TraceUtil.beginSection("releaseOutputBuffer");
        codec.releaseOutputBuffer(bufferIndex, true);
        TraceUtil.endSection();
        decoderCounters.renderedOutputBufferCount++;
        consecutiveDroppedFrameCount = 0;
        if (!renderedFirstFrame) {
            renderedFirstFrame = true;
            eventDispatcher.renderedFirstFrame(surface);
        }
    }

    @TargetApi(21)
    private void renderOutputBufferV21(MediaCodec codec, int bufferIndex, long releaseTimeNs) {
        maybeNotifyVideoSizeChanged();
        TraceUtil.beginSection("releaseOutputBuffer");
        codec.releaseOutputBuffer(bufferIndex, releaseTimeNs);
        TraceUtil.endSection();
        decoderCounters.renderedOutputBufferCount++;
        consecutiveDroppedFrameCount = 0;
        if (!renderedFirstFrame) {
            renderedFirstFrame = true;
            eventDispatcher.renderedFirstFrame(surface);
        }
    }

    @SuppressLint("InlinedApi")
    private static MediaFormat getMediaFormat(Format format, CodecMaxValues codecMaxValues,
                                              boolean deviceNeedsAutoFrcWorkaround) {
        MediaFormat frameworkMediaFormat = format.getFrameworkMediaFormatV16();
        frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, codecMaxValues.width);
        frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, codecMaxValues.height);

        if (codecMaxValues.inputSize != Format.NO_VALUE) {
            frameworkMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxValues.inputSize);
        }
        if (deviceNeedsAutoFrcWorkaround) {
            frameworkMediaFormat.setInteger("auto-frc", 0);
        }
        return frameworkMediaFormat;
    }

    private static CodecMaxValues getCodecMaxValues(Format format, Format[] streamFormats) {
        int maxWidth = format.width;
        int maxHeight = format.height;
        int maxInputSize = getMaxInputSize(format);
        for (Format streamFormat : streamFormats) {
            if (areAdaptationCompatible(format, streamFormat)) {
                maxWidth = Math.max(maxWidth, streamFormat.width);
                maxHeight = Math.max(maxHeight, streamFormat.height);
                maxInputSize = Math.max(maxInputSize, getMaxInputSize(streamFormat));
            }
        }
        return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
    }

    private void maybeNotifyDroppedFrames() {
        if (droppedFrames > 0) {
            long now = SystemClock.elapsedRealtime();
            long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
            eventDispatcher.droppedFrames(droppedFrames, elapsedMs);
            droppedFrames = 0;
            droppedFrameAccumulationStartTimeMs = now;
        }
    }

    private void maybeNotifyVideoSizeChanged() {
        if (lastReportedWidth != currentWidth || lastReportedHeight != currentHeight
                || lastReportedUnappliedRotationDegrees != currentUnappliedRotationDegrees
                || lastReportedPixelWidthHeightRatio != currentPixelWidthHeightRatio) {
            eventDispatcher.videoSizeChanged(currentWidth, currentHeight, currentUnappliedRotationDegrees,
                    currentPixelWidthHeightRatio);
            lastReportedWidth = currentWidth;
            lastReportedHeight = currentHeight;
            lastReportedUnappliedRotationDegrees = currentUnappliedRotationDegrees;
            lastReportedPixelWidthHeightRatio = currentPixelWidthHeightRatio;
        }
    }

    private static int getMaxInputSize(Format format) {
        if (format.maxInputSize != Format.NO_VALUE) {
            return format.maxInputSize;
        }

        if (format.width == Format.NO_VALUE || format.height == Format.NO_VALUE) {
            return Format.NO_VALUE;
        }

        int maxPixels;
        int minCompressionRatio;
        switch (format.sampleMimeType) {
            case MimeTypes.VIDEO_H263:
            case MimeTypes.VIDEO_MP4V:
                maxPixels = format.width * format.height;
                minCompressionRatio = 2;
                break;
            case MimeTypes.VIDEO_H264:
                if ("BRAVIA 4K 2015".equals(Util.MODEL)) {
                    return Format.NO_VALUE;
                }
                maxPixels = ((format.width + 15) / 16) * ((format.height + 15) / 16) * 16 * 16;
                minCompressionRatio = 2;
                break;
            case MimeTypes.VIDEO_VP8:
                maxPixels = format.width * format.height;
                minCompressionRatio = 2;
                break;
            case MimeTypes.VIDEO_H265:
            case MimeTypes.VIDEO_VP9:
                maxPixels = format.width * format.height;
                minCompressionRatio = 4;
                break;
            default:
                return Format.NO_VALUE;
        }
        return (maxPixels * 3) / (2 * minCompressionRatio);
    }

    private static boolean areAdaptationCompatible(Format first, Format second) {
        return first.sampleMimeType.equals(second.sampleMimeType)
                && getRotationDegrees(first) == getRotationDegrees(second);
    }

    private static float getPixelWidthHeightRatio(Format format) {
        return format.pixelWidthHeightRatio == Format.NO_VALUE ? 1 : format.pixelWidthHeightRatio;
    }

    private static int getRotationDegrees(Format format) {
        return format.rotationDegrees == Format.NO_VALUE ? 0 : format.rotationDegrees;
    }

    private static final class CodecMaxValues {
        public final int width;
        public final int height;
        public final int inputSize;

        public CodecMaxValues(int width, int height, int inputSize) {
            this.width = width;
            this.height = height;
            this.inputSize = inputSize;
        }
    }
}
