package com.yalin.exoplayer.source.smoothstreaming;

import com.yalin.exoplayer.extractor.mp4.TrackEncryptionBox;
import com.yalin.exoplayer.source.chunk.ChunkSource;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.upstream.LoaderErrorThrower;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public interface SsChunkSource extends ChunkSource {
    interface Factory {
        SsChunkSource createChunkSouce(LoaderErrorThrower manifestLoaderErrorThrower,
                                       SsManifest manifest, int elementIndex, TrackSelection trackSelection,
                                       TrackEncryptionBox[] trackEncryptionBoxes);
    }

    void updateManifest(SsManifest newManifest);
}
