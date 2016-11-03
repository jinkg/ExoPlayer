package com.yalin.exoplayer.upstream;

import android.content.Context;
import android.net.Uri;

import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class DefaultDataSource implements DataSource {

    private static final String SCHEME_ASSET = "asset";
    private static final String SCHEME_CONTENT = "content";

    private final DataSource baseDataSource;
    private final DataSource fileDataSource;
    private final DataSource assetDataSource;
    private final DataSource contentDataSource;

    private DataSource dataSource;

    public DefaultDataSource(Context context, TransferListener<? super DataSource> listener,
                             String userAgent, boolean allowCrossProtocolRedirects) {
        this(context, listener, userAgent, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects);
    }

    public DefaultDataSource(Context context, TransferListener<? super DataSource> listener,
                             String userAgent, int connectTimeoutMillis, int readTimeoutMillis,
                             boolean allowCrossProtocolRedirects) {
        this(context, listener,
                new DefaultHttpDataSource(userAgent, null, listener, connectTimeoutMillis,
                        readTimeoutMillis, allowCrossProtocolRedirects));
    }

    public DefaultDataSource(Context context, TransferListener<? super DataSource> listener,
                             DataSource baseDataSource) {
        this.baseDataSource = Assertions.checkNotNull(baseDataSource);
        this.fileDataSource = new FileDataSource(listener);
        this.assetDataSource = new AssetDataSource(context, listener);
        this.contentDataSource = new ContentDataSource(context, listener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Assertions.checkState(dataSource == null);
        String scheme = dataSpec.uri.getScheme();
        if (Util.isLocalFileUri(dataSpec.uri)) {
            if (dataSpec.uri.getPath().startsWith("/android_asset")) {
                dataSource = assetDataSource;
            } else {
                dataSource = fileDataSource;
            }
        } else if (SCHEME_ASSET.equals(scheme)) {
            dataSource = assetDataSource;
        } else if (SCHEME_CONTENT.equals(scheme)) {
            dataSource = contentDataSource;
        } else {
            dataSource = baseDataSource;
        }
        return dataSource.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return dataSource.read(buffer, offset, readLength);
    }

    @Override
    public Uri getUri() {
        return dataSource == null ? null : dataSource.getUri();
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            try {
                dataSource.close();
            } finally {
                dataSource = null;
            }
        }
    }
}
