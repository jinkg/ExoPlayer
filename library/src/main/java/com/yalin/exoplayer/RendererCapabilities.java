package com.yalin.exoplayer;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface RendererCapabilities {
    int FORMAT_SUPPORT_MASK = 0b11;

    int FORMAT_HANDLED = 0b11;

    int FORMAT_EXCEEDS_CAPABILITIES = 0b10;

    int FORMAT_UNSUPPORTED_SUBTYPE = 0b01;

    int FORMAT_UNSUPPORTED_TYPE = 0b00;

    int ADAPTIVE_SUPPORT_MASK = 0b1100;

    int ADAPTIVE_SEAMLESS = 0b1000;

    int ADAPTIVE_NOT_SEAMLESS = 0b0100;

    int ADAPTIVE_NOT_SUPPORTED = 0b0000;

    int getTrackType();

    int supportsFormat(Format format) throws ExoPlaybackException;

    int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException;
}
