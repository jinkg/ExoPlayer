package com.yalin.exoplayer.upstream;

import android.support.annotation.NonNull;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.util.Assertions;

import java.io.IOException;
import java.io.InputStream;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class DataSourceInputStream extends InputStream {
    private final DataSource dataSource;
    private final DataSpec dataSpec;
    private final byte[] singleByteArray;

    private boolean opened = false;
    private boolean closed = false;
    private long totalBytesRead;

    public DataSourceInputStream(DataSource dataSource, DataSpec dataSpec) {
        this.dataSource = dataSource;
        this.dataSpec = dataSpec;
        singleByteArray = new byte[1];
    }

    public long bytesRead() {
        return totalBytesRead;
    }

    public void open() throws IOException {
        checkOpened();
    }

    @Override
    public int read() throws IOException {
        int length = read(singleByteArray);
        if (length == -1) {
            return -1;
        }
        totalBytesRead++;
        return singleByteArray[0] & 0xFF;
    }

    @Override
    public int read(@NonNull byte[] buffer) throws IOException {
        int bytesRead = read(buffer, 0, buffer.length);
        if (bytesRead != -1) {
            totalBytesRead += bytesRead;
        }
        return bytesRead;
    }

    @Override
    public int read(@NonNull byte[] buffer, int off, int len) throws IOException {
        Assertions.checkState(!closed);
        checkOpened();
        int bytesRead = dataSource.read(buffer, off, len);
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            return -1;
        } else {
            totalBytesRead += bytesRead;
            return bytesRead;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        Assertions.checkState(!closed);
        checkOpened();
        long bytesSkipped = super.skip(n);
        totalBytesRead += bytesSkipped;
        return bytesSkipped;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            dataSource.close();
            closed = true;
        }
    }

    private void checkOpened() throws IOException {
        if (!opened) {
            dataSource.open(dataSpec);
            opened = true;
        }
    }
}
