package com.yalin.exoplayer.source;

/**
 * Created by YaLin
 * On 2016/11/1.
 */

public interface SequenceableLoader {
    interface Callback<T extends SequenceableLoader> {
        void onContinueLoadingRequested(T source);
    }

    long getNextLoadPositionUs();

    boolean continueLoading(long positionUs);
}
