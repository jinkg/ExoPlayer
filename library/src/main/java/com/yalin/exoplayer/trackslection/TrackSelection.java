package com.yalin.exoplayer.trackslection;

import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.source.TrackGroup;
import com.yalin.exoplayer.source.chunk.MediaChunk;

import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface TrackSelection {
    interface Factory {
        TrackSelection createTrackSelection(TrackGroup group, int... tracks);
    }

    TrackGroup getTrackGroup();

    int length();

    Format getFormat(int index);

    int getIndexInTrackGroup(int index);

    int indexOf(Format format);

    int indexOf(int indexInTrackGroup);

    Format getSelectedFormat();

    int getSelectedIndexInTrackGroup();

    int getSelectedIndex();

    int getSelectionReason();

    Object getSelectionData();

    void updateSelectedTrack(long bufferedDurationUs);

    int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue);

    boolean blacklist(int index, long blacklistDurationMs);
}
