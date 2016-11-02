package com.yalin.exoplayer.trackslection;

import android.os.Handler;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class MappingTrackSelector extends TrackSelector<MappingTrackSelector.MappedTrackInfo>{

    public MappingTrackSelector(Handler eventHandler) {
        super(eventHandler);
    }

    public static final class MappedTrackInfo{

    }
}
