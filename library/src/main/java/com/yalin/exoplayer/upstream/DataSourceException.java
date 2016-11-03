package com.yalin.exoplayer.upstream;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class DataSourceException extends IOException {
    public static final int POSITION_OUT_OF_RANGE = 0;

    public final int reason;

    public DataSourceException(int reason) {
        this.reason = reason;
    }
}
