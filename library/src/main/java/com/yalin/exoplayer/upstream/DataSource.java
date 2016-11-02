package com.yalin.exoplayer.upstream;

import android.net.Uri;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public interface DataSource {
    interface Factory {
        DataSource creteDataSource();
    }

    long open(DataSpec dataSpec) throws IOException;

    int read(byte[] buffer, int offset, int readLength) throws IOException;

    Uri getUri();

    void close() throws IOException;
}
