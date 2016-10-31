package com.yalin.exoplayer.mediacodec;

import com.yalin.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface MediaCodecSelector {
    MediaCodecSelector DEFAULT = new MediaCodecSelector() {
        @Override
        public MediaCodecInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder)
                throws DecoderQueryException {
            return MediaCodecUtil.getDecoderInfo(mimeType, requiresSecureDecoder);
        }

        @Override
        public MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
            return MediaCodecUtil.getPassthroughDecoderInfo();
        }
    };

    MediaCodecInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder) throws DecoderQueryException;

    MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException;
}
