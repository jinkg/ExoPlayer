package com.yalin.exoplayer.video;

import android.content.Context;
import android.os.Handler;
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
import com.yalin.exoplayer.util.Util;
import com.yalin.exoplayer.video.VideoRendererEventListener.EventDispatcher;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class MediaCodecVideoRenderer extends MediaCodecRenderer {

    private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
    private final EventDispatcher eventDispatcher;
    private final long allowedJoiningTimeMs;
    private final int videoScalingMode;
    private final int maxDroppedFramesToNotify;
    private final boolean deviceNeedsAutoFrcWorkaround;

    private Format[] streamFormats;

    private Surface surface;
    private boolean renderedFirstFrame;
    private long joiningDeadlineMs;
    private long droppedFrameAccumulationStartTimeMs;
    private int droppedFrames;
    private int consecutiveDroppedFrameCount;

    private float pendingPixelWidthHeightRatio;
    private int currentWidth;
    private int currentHeight;
    private float currentPixelWidthHeightRatio;
    private int lastReportedWidth;
    private int lastReportedHeight;
    private int lastReportedUnappliedRotationDegrees;
    private float lastReportedPixelWidthHeightRatio;

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                   int videoScalingMode, long allowedJoiningTimeMs,
                                   DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                   boolean playClearSamplesWithoutKeys, Handler eventHandler,
                                   VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(C.TRACK_TYPE_VIDEO, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys);
        this.videoScalingMode = videoScalingMode;
        this.allowedJoiningTimeMs = allowedJoiningTimeMs;
        this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
        frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper();
        eventDispatcher = new EventDispatcher();
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
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {

    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public boolean isEnded() {
        return false;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
        return 0;
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
}
