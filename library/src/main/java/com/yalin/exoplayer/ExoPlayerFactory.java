package com.yalin.exoplayer;

import android.content.Context;

import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;
import com.yalin.exoplayer.trackslection.TrackSelector;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class ExoPlayerFactory {
    public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

    private ExoPlayerFactory() {
    }

    public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector<?> trackSelector,
                                                    LoadControl loadControl,
                                                    DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                                    boolean preferExtensionDecoders) {
        return newSimpleInstance(context, trackSelector, loadControl, drmSessionManager,
                preferExtensionDecoders, DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);
    }

    private static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector<?> trackSelector,
                                                     LoadControl loadControl,
                                                     DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                                     boolean preferExtensionDecoders, long allowedVideoJoiningTimeMs) {
        return new SimpleExoPlayer(context, trackSelector, loadControl, drmSessionManager,
                preferExtensionDecoders, allowedVideoJoiningTimeMs);
    }
}
