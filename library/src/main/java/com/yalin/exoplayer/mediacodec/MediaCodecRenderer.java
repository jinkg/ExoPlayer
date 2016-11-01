package com.yalin.exoplayer.mediacodec;

import com.yalin.exoplayer.BaseRenderer;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class MediaCodecRenderer extends BaseRenderer {

    public MediaCodecRenderer(int trackType, MediaCodecSelector mediaCodecSelector,
                              DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                              boolean playClearSamplesWithoutKeys) {
        super(trackType);
    }

    protected abstract int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
            throws MediaCodecUtil.DecoderQueryException;

}
