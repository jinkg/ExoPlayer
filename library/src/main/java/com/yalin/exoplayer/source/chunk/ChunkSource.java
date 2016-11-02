package com.yalin.exoplayer.source.chunk;

import java.io.IOException;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public interface ChunkSource {
    void maybeThrowError() throws IOException;

    int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue);

    void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out);

    void onChunkLoadCompleted(Chunk chunk);

    boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e);
}
