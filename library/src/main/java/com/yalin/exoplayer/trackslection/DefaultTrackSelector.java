package com.yalin.exoplayer.trackslection;

import android.os.Handler;

import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.RendererCapabilities;
import com.yalin.exoplayer.source.TrackGroupArray;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class DefaultTrackSelector extends MappingTrackSelector {

    public DefaultTrackSelector(Handler eventHandler,
                                TrackSelection.Factory adaptiveVideoTrackSelectionFactory) {
        super(eventHandler);
    }

    @Override
    public TrackSelections<MappedTrackInfo> selectTracks(RendererCapabilities[] rendererCapabilities, TrackGroupArray trackGroups) throws ExoPlaybackException {
        return null;
    }
}
