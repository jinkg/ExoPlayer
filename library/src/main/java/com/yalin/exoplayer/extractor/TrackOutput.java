package com.yalin.exoplayer.extractor;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface TrackOutput {
    void format(Format format);

    int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
            throws IOException, InterruptedException;

    void sampleData(ParsableByteArray data, int length);

    void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
                        byte[] encryptionKey);
}
