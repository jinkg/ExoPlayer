package com.yalin.exoplayer.extractor;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface ExtractorOutput {
    TrackOutput track(int trackId);

    void endTracks();

    void seekMap(SeekMap seekMap);
}
