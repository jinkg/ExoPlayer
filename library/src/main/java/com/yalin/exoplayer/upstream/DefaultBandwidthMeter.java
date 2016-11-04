package com.yalin.exoplayer.upstream;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class DefaultBandwidthMeter implements BandwidthMeter, TransferListener<Object> {
    @Override
    public long getBitrateEstimate() {
        return 0;
    }

    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {

    }

    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {

    }

    @Override
    public void onTransferEnd(Object source) {

    }
}
