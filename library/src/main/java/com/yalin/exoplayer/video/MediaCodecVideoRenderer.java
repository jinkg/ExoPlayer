package com.yalin.exoplayer.video;

import android.content.Context;
import android.os.Handler;

import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;
import com.yalin.exoplayer.mediacodec.MediaCodecRenderer;
import com.yalin.exoplayer.mediacodec.MediaCodecSelector;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class MediaCodecVideoRenderer extends MediaCodecRenderer {

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                   int videoScalingMode, long allowedJoiningTimeMs,
                                   DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                   boolean playClearSamplesWithoutKeys, Handler eventHandler,
                                   ) {

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
}
