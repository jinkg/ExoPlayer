package com.yalin.exoplayer.upstream;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public interface TransferListener<S> {
    void onTransferStart(S source, DataSpec dataSpec);

    void onBytesTransferred(S source, int bytesTransferred);

    void onTransferEnd(S source);
}
