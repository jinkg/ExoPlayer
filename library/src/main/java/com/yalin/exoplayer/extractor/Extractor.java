package com.yalin.exoplayer.extractor;

import com.yalin.exoplayer.C;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface Extractor {
    int RESULT_CONTINUE = 0;

    int RESULT_SEEK = 1;

    int RESULT_END_OF_INPUT = C.RESULT_END_OF_INPUT;

    boolean sniff(ExtractorInput input) throws IOException, InterruptedException;

    void init(ExtractorOutput output);

    int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException;

    void seek(long position);

    void release();
}
