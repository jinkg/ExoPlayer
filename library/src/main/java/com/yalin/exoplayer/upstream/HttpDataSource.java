package com.yalin.exoplayer.upstream;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface HttpDataSource extends DataSource {
    interface Factory extends DataSource.Factory {
        @Override
        HttpDataSource creteDataSource();
    }


}
