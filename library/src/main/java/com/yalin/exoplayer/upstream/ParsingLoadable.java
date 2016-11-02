package com.yalin.exoplayer.upstream;

import android.net.Uri;

import com.yalin.exoplayer.upstream.Loader.Loadable;

import java.io.IOException;
import java.io.InputStream;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class ParsingLoadable<T> implements Loadable {
    public interface Parser<T> {
        T parse(Uri uri, InputStream inputStream) throws IOException;
    }

    public final DataSpec dataSpec;

    public final int type;

    private final DataSource dataSource;
    private final Parser<T> parser;

    private volatile T result;
    private volatile boolean isCanceled;
    private volatile long bytesLoaded;

    public ParsingLoadable(DataSource dataSource, Uri uri, int type, Parser<T> parser) {
        this.dataSource = dataSource;
        this.dataSpec = new DataSpec(uri, DataSpec.FLAG_ALLOW_GZIP);
        this.type = type;
        this.parser = parser;
    }

    public final T getResult() {
        return result;
    }

    public long bytesLoaded() {
        return bytesLoaded;
    }

    @Override
    public void cancelLoad() {
        isCanceled = true;
    }

    @Override
    public boolean isLoadCanceled() {
        return isCanceled;
    }

    @Override
    public void load() throws IOException, InterruptedException {
        DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
        try {
            inputStream.open();
            result = parser.parse(dataSource.getUri(), inputStream);
        } finally {
            //noinspection ThrowFromFinallyBlock
            inputStream.close();
            bytesLoaded = inputStream.bytesRead();
        }
    }
}
