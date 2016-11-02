package com.yalin.exoplayer.upstream;

import android.content.Context;

import com.yalin.exoplayer.upstream.DataSource.Factory;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class DefaultDataSourceFactory implements Factory {
    public DefaultDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, null);
    }

    public DefaultDataSourceFactory(Context context, String userAgent,
                                    TransferListener<? extends DataSource> listener) {
        this(context, listener, new DefaultHttpDataSourceFactory(userAgent, listener));
    }

    public DefaultDataSourceFactory(Context context, TransferListener<? extends DataSource> listener,
                                    DataSource.Factory baseDataSourceFactory) {
    }

    @Override
    public DataSource creteDataSource() {
        return new DefaultDataSource();
    }
}
