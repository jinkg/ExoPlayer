package com.yalin.exoplayer.upstream;


import com.yalin.exoplayer.upstream.HttpDataSource.Factory;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class DefaultHttpDataSourceFactory implements Factory {

    private final String userAgent;
    private final TransferListener<? super DataSource> listener;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final boolean allowCrossProtocolRedirects;


    public DefaultHttpDataSourceFactory(String userAgent) {
        this(userAgent, null);
    }

    public DefaultHttpDataSourceFactory(String userAgent, TransferListener<? super DataSource> listener) {
        this(userAgent, listener, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, false);
    }

    public DefaultHttpDataSourceFactory(String userAgent,
                                        TransferListener<? super DataSource> listener, int connectTimeoutMillis,
                                        int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = userAgent;
        this.listener = listener;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    }

    @Override
    public HttpDataSource createDataSource() {
        return new DefaultHttpDataSource(userAgent, null, listener, connectTimeoutMillis,
                readTimeoutMillis, allowCrossProtocolRedirects);
    }
}
