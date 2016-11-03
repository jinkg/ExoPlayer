package com.yalin.exoplayer.upstream;

import android.content.Context;

import com.yalin.exoplayer.upstream.DataSource.Factory;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class DefaultDataSourceFactory implements Factory {

    private final Context context;
    private final TransferListener<? super DataSource> listener;
    private final DataSource.Factory baseDataSourceFactory;

    public DefaultDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, null);
    }

    public DefaultDataSourceFactory(Context context, String userAgent,
                                    TransferListener<? super DataSource> listener) {
        this(context, listener, new DefaultHttpDataSourceFactory(userAgent, listener));
    }

    public DefaultDataSourceFactory(Context context, TransferListener<? super DataSource> listener,
                                    DataSource.Factory baseDataSourceFactory) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.baseDataSourceFactory = baseDataSourceFactory;
    }

    @Override
    public DataSource createDataSource() {
        return new DefaultDataSource(context, listener, baseDataSourceFactory.createDataSource());
    }
}
