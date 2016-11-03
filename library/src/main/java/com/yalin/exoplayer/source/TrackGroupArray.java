package com.yalin.exoplayer.source;

import com.yalin.exoplayer.C;

import java.util.Arrays;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public final class TrackGroupArray {

    public final int length;

    private final TrackGroup[] trackGroups;

    private int hashCode;

    public TrackGroupArray(TrackGroup... trackGroups) {
        this.trackGroups = trackGroups;
        this.length = trackGroups.length;
    }

    public TrackGroup get(int index) {
        return trackGroups[index];
    }

    public int indexOf(TrackGroup group) {
        for (int i = 0; i < length; i++) {
            if (trackGroups[i] == group) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Arrays.hashCode(trackGroups);
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
        TrackGroupArray other = (TrackGroupArray) obj;
        return length == other.length && Arrays.equals(trackGroups, other.trackGroups);
    }
}
