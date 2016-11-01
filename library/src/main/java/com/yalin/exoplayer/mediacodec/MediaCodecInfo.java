package com.yalin.exoplayer.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.util.Pair;

import com.yalin.exoplayer.util.MimeTypes;
import com.yalin.exoplayer.util.Util;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */
@TargetApi(16)
public final class MediaCodecInfo {
    public final String name;

    public final boolean adaptive;

    private final String mimeType;
    private final CodecCapabilities capabilities;

    public static MediaCodecInfo newInstance(String name, String mimeType,
                                             CodecCapabilities codecCapabilities) {
        return new MediaCodecInfo(name, mimeType, codecCapabilities);
    }

    public static MediaCodecInfo newPassthroughInstance(String name) {
        return new MediaCodecInfo(name, null, null);
    }

    public MediaCodecInfo(String name, String mimeType, CodecCapabilities capabilities) {
        this.name = name;
        this.mimeType = mimeType;
        this.capabilities = capabilities;
        this.adaptive = capabilities != null && isAdaptive(capabilities);
    }

    public CodecProfileLevel[] getprofileLevels() {
        return capabilities == null || capabilities.profileLevels == null ? new CodecProfileLevel[0]
                : capabilities.profileLevels;
    }

    public boolean isCodecSupported(String codec) {
        if (codec == null || mimeType == null) {
            return true;
        }
        String codecMimeType = MimeTypes.getMediaMimeType(codec);
        if (codecMimeType == null) {
            return true;
        }
        if (!mimeType.equals(codecMimeType)) {
            return false;
        }
        Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(codec);
        if (codecProfileAndLevel == null) {
            return true;
        }
        for (CodecProfileLevel capabilities : getprofileLevels()) {
            if (capabilities.profile == codecProfileAndLevel.first
                    && capabilities.level >= codecProfileAndLevel.second) {
                return true;
            }
        }
        return false;
    }

    @TargetApi(21)
    public boolean isVideoSizeAndRateSupportedV21(int width, int height, double frameRate) {
        if (capabilities == null) {
            return false;
        }
        VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        return videoCapabilities != null && videoCapabilities.areSizeAndRateSupported(width, height
                , frameRate);
    }

    @TargetApi(21)
    public boolean isVideoSizeSupportedV21(int width, int height) {
        if (capabilities == null) {
            return false;
        }
        VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        return videoCapabilities != null && videoCapabilities.isSizeSupported(width, height);
    }

    private static boolean isAdaptive(CodecCapabilities capabilities) {
        return Util.SDK_INT >= 19 && isAdaptiveV19(capabilities);
    }

    @TargetApi(19)
    private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
        return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
    }
}
