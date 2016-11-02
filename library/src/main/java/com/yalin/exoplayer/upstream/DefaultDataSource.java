package com.yalin.exoplayer.upstream;

import android.net.Uri;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class DefaultDataSource implements DataSource {
    @Override
    public long open(DataSpec dataSpec) throws IOException {
        return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return 0;
    }

    @Override
    public Uri getUri() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
