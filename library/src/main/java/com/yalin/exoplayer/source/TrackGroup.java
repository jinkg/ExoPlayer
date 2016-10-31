package com.yalin.exoplayer.source;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.util.Assertions;

import java.util.Arrays;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class TrackGroup {
    public final int length;

    private final Format[] formats;

    private int hashCode;

    public TrackGroup(Format... formats) {
        Assertions.checkState(formats.length > 0);
        this.formats = formats;
        this.length = formats.length;
    }

    public Format getFormat(int index) {
        return formats[index];
    }

    public int indexOf(Format format) {
        for (int i = 0; i < formats.length; i++) {
            if (format == formats[i]) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = 17;
            result = 31 * result + Arrays.hashCode(formats);
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
        TrackGroup other = (TrackGroup) obj;
        return length == other.length && Arrays.equals(formats, other.formats);
    }
}
