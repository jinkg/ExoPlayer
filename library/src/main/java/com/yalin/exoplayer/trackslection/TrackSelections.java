package com.yalin.exoplayer.trackslection;

import java.util.Arrays;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class TrackSelections<T> {
    public final T info;

    public final int length;

    private final TrackSelection[] trackSelections;

    private int hashCode;

    public TrackSelections(T info, TrackSelection[] trackSelections, int length) {
        this.info = info;
        this.trackSelections = trackSelections;
        this.length = length;
    }

    public TrackSelection get(int index) {
        return trackSelections[index];
    }

    public TrackSelection[] getAll() {
        return trackSelections.clone();
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = 17;
            result = 31 * result + Arrays.hashCode(trackSelections);
            hashCode = result;
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TrackSelections<?> other = (TrackSelections<?>) obj;
        return Arrays.equals(trackSelections, other.trackSelections);
    }
}
