package com.yalin.exoplayer.demo;

import android.app.Application;

import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.DefaultBandwidthMeter;
import com.yalin.exoplayer.upstream.DefaultDataSourceFactory;
import com.yalin.exoplayer.upstream.DefaultHttpDataSourceFactory;
import com.yalin.exoplayer.upstream.HttpDataSource;
import com.yalin.exoplayer.util.Util;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class DemoApplication extends Application {

    protected String userAgent;

    @Override
    public void onCreate() {
        super.onCreate();
        userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
    }

    DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultDataSourceFactory(this, bandwidthMeter,
                buildHttpDataSourceFactory(bandwidthMeter));
    }

    HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultHttpDataSourceFactory(userAgent, bandwidthMeter);
    }

}
