package com.yalin.exoplayer.trackslection;

import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.source.TrackGroup;
import com.yalin.exoplayer.source.chunk.MediaChunk;

import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class AdaptiveVideoTrackSelection extends BaseTrackSelection {
    public static final class Factory implements TrackSelection.Factory {

        public Factory() {
        }

        @Override
        public AdaptiveVideoTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new AdaptiveVideoTrackSelection();
        }
    }

    @Override
    public TrackGroup getTrackGroup() {
        return null;
    }

    @Override
    public int length() {
        return 0;
    }

    @Override
    public Format getFormat(int index) {
        return null;
    }

    @Override
    public int getIndexInTrackGroup(int index) {
        return 0;
    }

    @Override
    public int indexOf(Format format) {
        return 0;
    }

    @Override
    public int indexOf(int indexInTrackGroup) {
        return 0;
    }

    @Override
    public Format getSelectedFormat() {
        return null;
    }

    @Override
    public int getSelectedIndexInTrackGroup() {
        return 0;
    }

    @Override
    public int getSelectedIndex() {
        return 0;
    }

    @Override
    public int getSelectionReason() {
        return 0;
    }

    @Override
    public Object getSelectionData() {
        return null;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {

    }

    @Override
    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        return 0;
    }

    @Override
    public boolean blacklist(int index, long blacklistDurationMs) {
        return false;
    }
}
