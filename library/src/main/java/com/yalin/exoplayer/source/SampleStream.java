package com.yalin.exoplayer.source;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface SampleStream {
    boolean isReady();

    void maybeThrowError() throws IOException;
}
