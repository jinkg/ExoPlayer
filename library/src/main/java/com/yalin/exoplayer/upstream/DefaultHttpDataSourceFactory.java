package com.yalin.exoplayer.upstream;


import com.yalin.exoplayer.upstream.HttpDataSource.Factory;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class DefaultHttpDataSourceFactory implements Factory {

    public DefaultHttpDataSourceFactory(String userAgent) {

    }

    public DefaultHttpDataSourceFactory(String userAgent, TransferListener<? super DataSource> listener) {

    }

    @Override
    public HttpDataSource creteDataSource() {
        return null;
    }
}
