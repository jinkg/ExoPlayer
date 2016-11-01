package com.yalin.exoplayer.decoder;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public final class DecoderCounters {
    public int decoderInitCount;

    public int decoderReleaseCount;

    public int inputBufferCount;

    public int renderedOutputBufferCount;

    public int skippedOutputBufferCount;

    public int droppedOutputBufferCount;

    public int maxConsecutiveDroppedOutputBufferCount;

    public synchronized void ensureUpdated() {

    }
}
