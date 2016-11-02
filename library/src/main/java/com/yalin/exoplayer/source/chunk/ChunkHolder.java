package com.yalin.exoplayer.source.chunk;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class ChunkHolder {
    public Chunk chunk;

    public boolean endOfStream;

    public void clear() {
        chunk = null;
        endOfStream = false;
    }
}
