package com.yalin.exoplayer.source.smoothstreaming;

import com.yalin.exoplayer.extractor.mp4.TrackEncryptionBox;
import com.yalin.exoplayer.source.chunk.Chunk;
import com.yalin.exoplayer.source.chunk.ChunkHolder;
import com.yalin.exoplayer.source.chunk.MediaChunk;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.LoaderErrorThrower;

import java.io.IOException;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public class DefaultSsChunkSource implements SsChunkSource {
    public static final class Factory implements SsChunkSource.Factory {

        private final DataSource.Factory dataSourceFactory;

        public Factory(DataSource.Factory dataSourceFactory) {
            this.dataSourceFactory = dataSourceFactory;
        }

        @Override
        public SsChunkSource createChunkSource(LoaderErrorThrower manifestLoaderErrorThrower,
                                               SsManifest manifest, int elementIndex, TrackSelection trackSelection,
                                               TrackEncryptionBox[] trackEncryptionBoxes) {
            return null;
        }
    }

    public DefaultSsChunkSource(LoaderErrorThrower manifestLoaderErrorThrower, SsManifest manifest,
                                int elementIndex, TrackSelection trackSelection, DataSource dataSource,
                                TrackEncryptionBox[] trackEncryptionBoxes) {

    }

    @Override
    public void updateManifest(SsManifest newManifest) {

    }

    @Override
    public void maybeThrowError() throws IOException {

    }

    @Override
    public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        return 0;
    }

    @Override
    public void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {

    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {

    }

    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
        return false;
    }
}
