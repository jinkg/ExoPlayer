package com.yalin.exoplayer.drm;

import android.annotation.TargetApi;
import android.media.MediaCrypto;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

@TargetApi(16)
public final class FrameworkMediaCrypto implements ExoMediaCrypto {
    private final MediaCrypto mediaCrypto;

    FrameworkMediaCrypto(MediaCrypto mediaCrypto) {
        this.mediaCrypto = mediaCrypto;
    }

    public MediaCrypto getWrappedMediaCrypto() {
        return mediaCrypto;
    }

    @Override
    public boolean requiresSecureDecoderComponent(String mimeType) {
        return mediaCrypto.requiresSecureDecoderComponent(mimeType);
    }
}
