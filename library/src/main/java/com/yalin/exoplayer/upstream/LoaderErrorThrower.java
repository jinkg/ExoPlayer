package com.yalin.exoplayer.upstream;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface LoaderErrorThrower {
    void maybeThrowError() throws IOException;

    void maybeThrowError(int minRetryCount) throws IOException;
}
