package com.yalin.exoplayer.source;

import com.yalin.exoplayer.FormatHolder;
import com.yalin.exoplayer.decoder.DecoderInputBuffer;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface SampleStream {
    boolean isReady();

    void maybeThrowError() throws IOException;

    int readData(FormatHolder formatHolder, DecoderInputBuffer buffer);

    void skipToKeyframeBefor(long timeUs);
}
