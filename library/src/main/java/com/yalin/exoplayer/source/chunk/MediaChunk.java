package com.yalin.exoplayer.source.chunk;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.DataSpec;
import com.yalin.exoplayer.util.Assertions;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class MediaChunk extends Chunk {

    /**
     * The chunk index.
     */
    public final int chunkIndex;

    /**
     * @param dataSource           The source from which the data should be loaded.
     * @param dataSpec             Defines the data to be loaded.
     * @param trackFormat          See {@link #trackFormat}.
     * @param trackSelectionReason See {@link #trackSelectionReason}.
     * @param trackSelectionData   See {@link #trackSelectionData}.
     * @param startTimeUs          The start time of the media contained by the chunk, in microseconds.
     * @param endTimeUs            The end time of the media contained by the chunk, in microseconds.
     * @param chunkIndex           The index of the chunk.
     */
    public MediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
                      int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs,
                      int chunkIndex) {
        super(dataSource, dataSpec, C.DATA_TYPE_MEDIA, trackFormat, trackSelectionReason,
                trackSelectionData, startTimeUs, endTimeUs);
        Assertions.checkNotNull(trackFormat);
        this.chunkIndex = chunkIndex;
    }

    /**
     * Returns the next chunk index.
     */
    public final int getNextChunkIndex() {
        return chunkIndex + 1;
    }

    /**
     * Returns whether the chunk has been fully loaded.
     */
    public abstract boolean isLoadCompleted();
}
