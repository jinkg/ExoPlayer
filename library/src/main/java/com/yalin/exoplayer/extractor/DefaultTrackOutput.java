package com.yalin.exoplayer.extractor;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class DefaultTrackOutput implements TrackOutput {
    public interface UpstreamFormatChangedListener {
        void onUpstreamFormatChanged(Format format);
    }

    @Override
    public void format(Format format) {

    }

    @Override
    public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public void sampleData(ParsableByteArray data, int length) {

    }

    @Override
    public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset, byte[] encryptionKey) {

    }
}
