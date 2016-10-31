package com.yalin.exoplayer.drm;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface ExoMediaCrypto {
    boolean requiresSecureDecoderComponent(String mimeType);
}
