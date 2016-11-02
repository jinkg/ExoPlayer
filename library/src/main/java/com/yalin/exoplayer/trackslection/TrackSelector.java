package com.yalin.exoplayer.trackslection;

import android.os.Handler;

import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.RendererCapabilities;
import com.yalin.exoplayer.source.TrackGroupArray;
import com.yalin.exoplayer.util.Assertions;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public abstract class TrackSelector<T> {
    public interface InvalidationListener {
        void onTrackSelectionsInvalidated();
    }

    public interface EventListener<T> {
        void onTrackSelectionsChanged(TrackSelections<? extends T> trackSelections);
    }

    private final Handler eventHandler;
    private final CopyOnWriteArraySet<MappingTrackSelector.EventListener<? super T>> listeners;

    private InvalidationListener listener;
    private TrackSelections<T> activeSelections;

    public TrackSelector(Handler eventHandler) {
        this.eventHandler = Assertions.checkNotNull(eventHandler);
        this.listeners = new CopyOnWriteArraySet<>();
    }

    public final void addListener(EventListener<? super T> listener) {
        listeners.add(listener);
    }

    public final void removeListener(EventListener<? super T> listener) {
        listeners.remove(listener);
    }

    public final void init(InvalidationListener listener) {
        this.listener = listener;
    }

    public final void onSelectionActivated(TrackSelections<T> activeSelections) {

    }

    public abstract TrackSelections<T> selectTracks(
            RendererCapabilities[] rendererCapabilities, TrackGroupArray trackGroups)
            throws ExoPlaybackException;
}
