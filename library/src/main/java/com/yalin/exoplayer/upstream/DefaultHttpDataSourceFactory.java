package com.yalin.exoplayer.upstream;

import com.yalin.exoplayer.upstream.DataSource.Factory;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class DefaultHttpDataSourceFactory implements Factory {

    public DefaultHttpDataSourceFactory(String userAgent){

    }

    public DefaultHttpDataSourceFactory(String userAgent, TransferListener<? extends DataSource> listener) {

    }

    @Override
    public DataSource creteDataSource() {
        return null;
    }
}
