package com.yalin.exoplayer.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;

import com.yalin.exoplayer.util.Util;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

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

    private static boolean isAdaptive(CodecCapabilities capabilities) {
        return Util.SDK_INT >= 19 && isAdaptiveV19(capabilities);
    }

    @TargetApi(19)
    private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
        return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
    }
}
